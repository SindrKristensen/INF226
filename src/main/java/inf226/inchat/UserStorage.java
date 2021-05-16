package inf226.inchat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import inf226.storage.*;


/**
 * The UserStore stores User objects in a SQL database.
 */
public final class UserStorage
    implements Storage<User,SQLException> {

    final ConnectionManager connectionManager;

    public UserStorage(Connection connection) 
      throws SQLException {
        this.connectionManager = new ConnectionManager(connection);
        String userTableQuery = "CREATE TABLE IF NOT EXISTS User (id TEXT PRIMARY KEY, version TEXT, name TEXT, joined TEXT)";
        connectionManager.prepareAndExecuteUpdate(userTableQuery, null);
    }
    
    @Override
    public Stored<User> save(User user)
      throws SQLException {
        final Stored<User> stored = new Stored<>(user);
        String saveUserQuery =  "INSERT INTO User VALUES(?,?,?,?)";
        String[] saveUserData = new String[]{stored.identity.toString(), stored.version.toString(), user.name.getUserName(), user.joined.toString()};
        connectionManager.prepareAndExecuteUpdate(saveUserQuery, saveUserData);
        return stored;
    }
    
    @Override
    public synchronized Stored<User> update(Stored<User> user,
                                            User new_user)
        throws UpdatedException,
            DeletedException,
            SQLException {
        final Stored<User> current = get(user.identity);
        final Stored<User> updated = current.newVersion(new_user);
        if(current.version.equals(user.version)) {
            String updateUserQuery = "UPDATE User SET (version,name,joined) =(?,?,?) WHERE id= ?";
            String[] updateUserData = new String[]{updated.version.toString(), new_user.name.getUserName(), new_user.joined.toString(), updated.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(updateUserQuery, updateUserData);
        } else {
            throw new UpdatedException(current);
        }
        return updated;
    }
   
    @Override
    public synchronized void delete(Stored<User> user)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<User> current = get(user.identity);
        if(current.version.equals(user.version)) {
            String deleteUserQuery =  "DELETE FROM User WHERE id ='" + user.identity + "'";
            String[] deleteUserData = new String[]{user.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(deleteUserQuery, deleteUserData);
        } else {
            throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<User> get(UUID id)
      throws DeletedException,
             SQLException {
        final String getUserQuery = "SELECT version,name,joined FROM User WHERE id = ?";
        final String[] getUserData = new String[]{id.toString()};
        final ResultSet rs = connectionManager.prepareAndExecuteQuery(getUserQuery, getUserData);

        if(rs.next()) {
            final UUID version = 
                UUID.fromString(rs.getString("version"));
            final String name = rs.getString("name");
            final Instant joined = Instant.parse(rs.getString("joined"));
            return (new Stored<>
                        (new User(name,joined),id,version));
        } else {
            throw new DeletedException();
        }
    }
}


