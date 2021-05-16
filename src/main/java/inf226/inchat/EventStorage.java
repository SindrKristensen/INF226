package inf226.inchat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import inf226.storage.*;




public final class EventStorage
    implements Storage<Channel.Event,SQLException> {

    private final ConnectionManager connectionManager;
    
    public EventStorage(Connection connection) 
      throws SQLException {
        this.connectionManager = new ConnectionManager(connection);

        String eventTableQuery = "CREATE TABLE IF NOT EXISTS Event (id TEXT PRIMARY KEY, version TEXT, type INTEGER, time TEXT)";
        String messageTableQuery = "CREATE TABLE IF NOT EXISTS Message (id TEXT PRIMARY KEY, sender TEXT, content Text, FOREIGN KEY(id) REFERENCES Event(id) ON DELETE CASCADE)";
        String joinedTableQuery = "CREATE TABLE IF NOT EXISTS Joined (id TEXT PRIMARY KEY, sender TEXT, FOREIGN KEY(id) REFERENCES Event(id) ON DELETE CASCADE)";
        connectionManager.prepareAndExecuteUpdate(eventTableQuery, null);
        connectionManager.prepareAndExecuteUpdate(messageTableQuery, null);
        connectionManager.prepareAndExecuteUpdate(joinedTableQuery, null);
    }
    
    @Override
    public Stored<Channel.Event> save(Channel.Event event)
      throws SQLException {
        
        final Stored<Channel.Event> stored = new Stored<Channel.Event>(event);

        String eventQuery =  "INSERT INTO Event VALUES(?,?,?,?)";
        String[] eventData = new String[]{stored.identity.toString(), stored.version.toString(), event.type.code.toString(), event.time.toString()};
        connectionManager.prepareAndExecuteUpdate(eventQuery, eventData);

        String query = "";
        String[] queryData = null;
        switch (event.type) {
            case message:

                query = "INSERT INTO Message VALUES(?,?,?)";
                queryData = new String[]{stored.identity.toString(), event.sender, event.message};
                break;
            case join:
                query = "INSERT INTO Joined VALUES(?,?)";
                queryData = new String[]{stored.identity.toString(), event.sender};
                break;
        }
        connectionManager.prepareAndExecuteUpdate(query, queryData);

        return stored;
    }
    
    @Override
    public synchronized Stored<Channel.Event> update(Stored<Channel.Event> event,
                                            Channel.Event new_event)
        throws UpdatedException,
            DeletedException,
            SQLException {
    final Stored<Channel.Event> current = get(event.identity);
    final Stored<Channel.Event> updated = current.newVersion(new_event);
    if(current.version.equals(event.version)) {
        String updateEventQuery = "UPDATE Event SET" +
            " (version,time,type) =(?,?,?) WHERE id= ?";
        String[] updateEventData = new String[]{updated.version.toString(), new_event.time.toString(), new_event.type.code.toString(), updated.identity.toString()};
        connectionManager.prepareAndExecuteUpdate(updateEventQuery, updateEventData);

        String updateQuery = "";
        String[] updateData = null;
        switch (new_event.type) {
            case message:
                updateQuery = "UPDATE Message SET (sender,content)=(?,?) WHERE id=?";
                updateData = new String[]{new_event.sender, new_event.message, updated.identity.toString()};
                break;
            case join:
                updateQuery = "UPDATE Joined SET (sender)=(?) WHERE id=?";
                updateData = new String[]{new_event.sender, updated.identity.toString()};
                break;
        }
        connectionManager.prepareAndExecuteUpdate(updateQuery, updateData);
    } else {
        throw new UpdatedException(current);
    }
        return updated;
    }
   
    @Override
    public synchronized void delete(Stored<Channel.Event> event)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<Channel.Event> current = get(event.identity);
        if(current.version.equals(event.version)) {
            String deleteEventQuery =  "DELETE FROM Event WHERE id = ?";
            String[] deleteEventData = new String[]{event.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(deleteEventQuery, deleteEventData);
        } else {
        throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<Channel.Event> get(UUID id)
      throws DeletedException,
             SQLException {
        final String getEventQuery = "SELECT version,time,type FROM Event WHERE id = ?";
        final String[] getEventData = new String[]{id.toString()};

        final ResultSet rs = connectionManager.prepareAndExecuteQuery(getEventQuery, getEventData);

        if(rs.next()) {
            final UUID version = UUID.fromString(rs.getString("version"));
            final Channel.Event.Type type = 
                Channel.Event.Type.fromInteger(rs.getInt("type"));
            final Instant time = 
                Instant.parse(rs.getString("time"));
            
            final String[] data = new String[]{id.toString()};
            switch(type) {
                case message:
                    final String messageQuery = "SELECT sender,content FROM Message WHERE id = ?";
                    final ResultSet mrs = connectionManager.prepareAndExecuteQuery(messageQuery, data);
                    mrs.next();
                    return new Stored<Channel.Event>(
                            Channel.Event.createMessageEvent(time,mrs.getString("sender"),mrs.getString("content")),
                            id,
                            version);
                case join:
                    final String joinedQuery = "SELECT sender FROM Joined WHERE id = ?";
                    final ResultSet ars = connectionManager.prepareAndExecuteQuery(joinedQuery, data);
                    ars.next();
                    return new Stored<Channel.Event>(
                            Channel.Event.createJoinEvent(time,ars.getString("sender")),
                            id,
                            version);
            }
        }
        throw new DeletedException();
    }
    
}


 
