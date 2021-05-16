package inf226.inchat;

import java.sql.*;
import java.util.UUID;

import inf226.storage.*;

import inf226.util.immutable.List;
import inf226.util.*;

/**
 * This class stores accounts in the database.
 */
public final class AccountStorage implements Storage<Account,SQLException> {

    final ConnectionManager connectionManager;
    final Storage<User,SQLException> userStore;
    final Storage<Channel,SQLException> channelStore;

    /**
     * Create a new account storage.
     *
     * @param  connection   The connection to the SQL database.
     * @param  userStore    The storage for User data.
     * @param  channelStore The storage for channels.
     */
    public AccountStorage(Connection connection,
                          Storage<User,SQLException> userStore,
                          Storage<Channel,SQLException> channelStore)
      throws SQLException {
        this.connectionManager = new ConnectionManager(connection);
        this.userStore = userStore;
        this.channelStore = channelStore;

        String accountSql = "CREATE TABLE IF NOT EXISTS Account (id TEXT PRIMARY KEY , version TEXT, user TEXT, key TEXT, FOREIGN KEY(user) REFERENCES User(id) ON DELETE CASCADE)";
        String channelSql = "CREATE TABLE IF NOT EXISTS AccountChannel (account TEXT, channel TEXT, alias TEXT, ordinal INTEGER, PRIMARY KEY(account,channel), FOREIGN KEY(account) REFERENCES Account(id) ON DELETE CASCADE, FOREIGN KEY(channel) REFERENCES Channel(id) ON DELETE CASCADE)";
        String channel_permsql = "CREATE TABLE IF NOT EXISTS Channel_permissions (channel TEXT not null,account TEXT not null, permission TEXT not null);";
        connectionManager.prepareAndExecuteUpdate(accountSql, null);
        connectionManager.prepareAndExecuteUpdate(channelSql, null);
        connectionManager.prepareAndExecuteUpdate(channel_permsql, null);
    }

    @Override
    public Stored<Account> save(Account account)
      throws SQLException {

        final Stored<Account> stored = new Stored<>(account);

        String accountQuery =  "INSERT INTO Account VALUES(?,?,?,?)";
        String[] accountData = new String[]{stored.identity.toString(), stored.version.toString(), account.user.identity.toString(), account.getPassword()};
        connectionManager.prepareAndExecuteUpdate(accountQuery, accountData);

        // Write the list of channels
        final Maybe.Builder<SQLException> exception = Maybe.builder();
        final Mutable<Integer> ordinal = new Mutable<>(0);
        account.channels.forEach(element -> {
            String alias = element.first;
            Stored<Channel> channel = element.second;
            final String accountChannelQuery = "INSERT INTO AccountChannel VALUES(?,?,?,?)";
            final String[] accountChannelData = new String[]{stored.identity.toString(), channel.identity.toString(), alias, ordinal.get().toString()};

            try { connectionManager.prepareAndExecuteUpdate(accountChannelQuery, accountChannelData); }
            catch (SQLException e) { exception.accept(e) ; }

            ordinal.accept(ordinal.get() + 1);
        });

        Util.throwMaybe(exception.getMaybe());
        return stored;
    }

    @Override
    public synchronized Stored<Account> update(Stored<Account> account,
                                            Account new_account)
        throws UpdatedException,
            DeletedException,
            SQLException {
    final Stored<Account> current = get(account.identity);
    final Stored<Account> updated = current.newVersion(new_account);
    if(current.version.equals(account.version)) {
        String accountUpdateQuery = "UPDATE Account SET (version,user) =(?,?) WHERE id= ?";
        String[] accountUpdateData = new String[]{updated.version.toString(), new_account.user.identity.toString(), updated.identity.toString()};
        connectionManager.prepareAndExecuteUpdate(accountUpdateQuery, accountUpdateData);

        // Rewrite the list of channels
        String deleteChannelQuery = "DELETE FROM AccountChannel WHERE account=?";
        String[] deleteChannelData = new String[]{account.identity.toString()};
        connectionManager.prepareAndExecuteUpdate(deleteChannelQuery, deleteChannelData);

        final Maybe.Builder<SQLException> exception = Maybe.builder();
        final Mutable<Integer> ordinal = new Mutable<Integer>(0);
        new_account.channels.forEach(element -> {
            String alias = element.first;
            Stored<Channel> channel = element.second;

            final String addChannelQuery = "INSERT INTO AccountChannel VALUES(?,?,?,?)";
            final String[] addChannelData = new String[]{account.identity.toString(), channel.identity.toString(), alias, ordinal.get().toString()};
            try { connectionManager.prepareAndExecuteUpdate(addChannelQuery, addChannelData); }
            catch (SQLException e) { exception.accept(e) ; }

            ordinal.accept(ordinal.get() + 1);
        });

        Util.throwMaybe(exception.getMaybe());
    } else {
        throw new UpdatedException(current);
    }
    return updated;
    }

    @Override
    public synchronized void delete(Stored<Account> account)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<Account> current = get(account.identity);
        if(current.version.equals(account.version)) {
            String deleteAccountQuery = "DELETE FROM Account WHERE id =?";
            String[] deleteAccountData = new String[]{account.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(deleteAccountQuery, deleteAccountData);
        } else {
            throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<Account> get(UUID id)
      throws DeletedException,
             SQLException {

        final String accountQuery = "SELECT version,user, key FROM Account WHERE id =?";
        final String channelQuery = "SELECT channel,alias,ordinal FROM AccountChannel WHERE account = ? ORDER BY ordinal DESC";
        final String[] data = new String[]{id.toString()};

        final ResultSet accountResult = connectionManager.prepareAndExecuteQuery(accountQuery, data);
        final ResultSet channelResult = connectionManager.prepareAndExecuteQuery(channelQuery, data);

        if(accountResult.next()) {
            final UUID version = UUID.fromString(accountResult.getString("version"));

            final UUID userid = UUID.fromString(accountResult.getString("user"));

            final String userKey = accountResult.getString("key");

            final Stored<User> user = userStore.get(userid);

            // Get all the channels associated with this account
            final List.Builder<Pair<String,Stored<Channel>>> channels = List.builder();

            while(channelResult.next()) {
                final UUID channelId = UUID.fromString(channelResult.getString("channel"));
                final String alias = channelResult.getString("alias");
                channels.accept(
                    new Pair<String,Stored<Channel>>(
                        alias,channelStore.get(channelId)));
            }
            return (new Stored<Account>(new Account(user,channels.getList(), userKey),id,version));
        } else {
            throw new DeletedException();
        }
    }

    //Retrieves password from database
    public String getPassword(String username)
            throws SQLException {
        final String passwordQuery = "SELECT key from Account INNER JOIN User ON user=User.id where User.name=?";
        final String[] passwordData = new String[]{username};

        final ResultSet rs = connectionManager.prepareAndExecuteQuery(passwordQuery, passwordData);
        String pwd = rs.getString("key");

        return pwd;
    }


    /**
     * Look up an account based on their username.
     */
    public Stored<Account> lookup(String username) throws DeletedException, SQLException {
        final String lookupAccountQuery = "SELECT Account.id from Account INNER JOIN User ON user=User.id where User.name= ?";
        final String[] lookupAccountData = new String[]{username};
        final ResultSet rs = connectionManager.prepareAndExecuteQuery(lookupAccountQuery, lookupAccountData);

        if(rs.next()) {
            final UUID identity =
                    UUID.fromString(rs.getString("id"));
            return get(identity);
        }
        throw new DeletedException();
    }

    /**
     * Check if a user has user access to the channel returns true or false
     */
    public boolean checkUserAccess(Stored<Account> account, Stored<Channel> channel) throws SQLException{
        String checkPermQuery = "SELECT * FROM Channel_permissions WHERE channel = ? AND account = ?;";
        String[] checkPermData = new String[]{channel.identity.toString(),account.identity.toString()};
        boolean hasNext = false;

        ResultSet rs = connectionManager.prepareAndExecuteQuery(checkPermQuery,checkPermData);
        hasNext = rs.next();

        return hasNext;
    }

    /**
     * Sets the user access from the database.
     */
    public void setUserAccess(Stored<Account> account, Stored<Channel> channel, String role) throws SQLException {
        String insertPermQuery = "INSERT INTO Channel_permissions VALUES (?, ?, ?);";
        String[] insertPermData = new String[]{channel.identity.toString(),account.identity.toString(),role};

        connectionManager.prepareAndExecuteUpdate(insertPermQuery,insertPermData);
    }

    /**
     * Updates the user access in the database.
     */
    public void updateUserAccess(Stored<Account> account, Stored<Channel> channel, String role) throws SQLException {
        String updatePermQuery = "UPDATE Channel_permissions SET permission = ? WHERE channel = ? AND account = ?;";
        String[] updatePermData = new String[]{role,channel.identity.toString(),account.identity.toString()};
        connectionManager.prepareAndExecuteUpdate(updatePermQuery,updatePermData);

    }

    /**
     * Retrieves the user access from the database
     */
    public String getUserAccess(Stored<Account> account, UUID channel) throws SQLException{
        String insertPermQuery = "SELECT * FROM Channel_permissions WHERE channel = ? AND account = ?;";
        String[] insertPermData = new String[]{channel.toString(),account.identity.toString()};

        final ResultSet rs = connectionManager.prepareAndExecuteQuery(insertPermQuery,insertPermData);

        return rs.getString("permission");
    }

    /**
     * Look up an user based on the UUID
     */
    public String getUserName( String UserUUID) throws SQLException {
        String nameUserQuery = "SELECT * FROM User WHERE id = ?;";
        String[] nameUserDate = new String[]{UserUUID};
        final ResultSet rs = connectionManager.prepareAndExecuteQuery(nameUserQuery,nameUserDate);
        return rs.getString("name");
    }

}

