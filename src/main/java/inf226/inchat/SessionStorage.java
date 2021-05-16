package inf226.inchat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import inf226.storage.*;

/**
 * The SessionStorage stores Session objects in a SQL database.
 */
public final class SessionStorage
    implements Storage<Session,SQLException> {

    final ConnectionManager connectionManager;
    final Storage<Account,SQLException> accountStorage;
    
    public SessionStorage(Connection connection,
                          Storage<Account,SQLException> accountStorage)
      throws SQLException {
        this.connectionManager = new ConnectionManager(connection);
        this.accountStorage = accountStorage;

        String sessionTableQuery = "CREATE TABLE IF NOT EXISTS Session (id TEXT PRIMARY KEY, version TEXT, account TEXT, expiry TEXT, FOREIGN KEY(account) REFERENCES Account(id) ON DELETE CASCADE)";
        connectionManager.prepareAndExecuteUpdate(sessionTableQuery, null);
    }
    
    @Override
    public Stored<Session> save(Session session)
      throws SQLException {
        
        final Stored<Session> stored = new Stored<Session>(session);
        String saveSessionQuery =  "INSERT INTO Session VALUES(?,?,?,?)";
        String[] saveSessionData = new String[]{stored.identity.toString(), stored.version.toString(), session.account.identity.toString(), session.expiry.toString()};
        connectionManager.prepareAndExecuteUpdate(saveSessionQuery, saveSessionData);
        return stored;
    }
    
    @Override
    public synchronized Stored<Session> update(Stored<Session> session,
                                            Session new_session)
        throws UpdatedException,
            DeletedException,
            SQLException {
        final Stored<Session> current = get(session.identity);
        final Stored<Session> updated = current.newVersion(new_session);
        if(current.version.equals(session.version)) {
            String updateSessionQuery = "UPDATE Session SET (version,account,expiry) =(?,?,?) WHERE id= ?";
            String[] updateSessionData = new String[]{updated.version.toString(), new_session.account.identity.toString(), new_session.expiry.toString(), updated.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(updateSessionQuery, updateSessionData);
        } else {
            throw new UpdatedException(current);
        }
        return updated;
    }
   
    @Override
    public synchronized void delete(Stored<Session> session)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<Session> current = get(session.identity);
        if(current.version.equals(session.version)) {
            String deleteQuery =  "DELETE FROM Session WHERE id = ?";
            String[] deleteData = new String[]{session.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(deleteQuery, deleteData);
        } else {
            throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<Session> get(UUID id)
      throws DeletedException,
             SQLException {
        final String getSessionQuery = "SELECT version,account,expiry FROM Session WHERE id = ?";
        final String[] getSessionData = new String[]{id.toString()};
        final ResultSet rs = connectionManager.prepareAndExecuteQuery(getSessionQuery, getSessionData);

        if(rs.next()) {
            final UUID version = UUID.fromString(rs.getString("version"));
            final Stored<Account> account
               = accountStorage.get(
                    UUID.fromString(rs.getString("account")));
            final Instant expiry = Instant.parse(rs.getString("expiry"));
            return (new Stored<Session>
                        (new Session(account,expiry),id,version));
        } else {
            throw new DeletedException();
        }
    }
}
