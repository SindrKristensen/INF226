package inf226.inchat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.TreeMap;
import java.util.Map;
import java.util.function.Consumer;

import inf226.storage.*;

import inf226.util.immutable.List;
import inf226.util.*;

/**
 * This class stores Channels in a SQL database.
 */
public final class ChannelStorage
    implements Storage<Channel,SQLException> {

    final ConnectionManager connectionManager;

    /* The waiters object represent the callbacks to
     * make when the channel is updated.
     */
    private Map<UUID,List<Consumer<Stored<Channel>>>> waiters
        = new TreeMap<UUID,List<Consumer<Stored<Channel>>>>();
    public final EventStorage eventStore;
    
    public ChannelStorage(Connection connection,
                          EventStorage eventStore) 
      throws SQLException {
        this.connectionManager = new ConnectionManager(connection);
        this.eventStore = eventStore;

        String channelTableQuery = "CREATE TABLE IF NOT EXISTS Channel (id TEXT PRIMARY KEY, version TEXT, name TEXT)";
        String eventTableQuery = "CREATE TABLE IF NOT EXISTS ChannelEvent (channel TEXT, event TEXT, ordinal INTEGER, PRIMARY KEY(channel,event), FOREIGN KEY(channel) REFERENCES Channel(id) ON DELETE CASCADE, FOREIGN KEY(event) REFERENCES Event(id) ON DELETE CASCADE)";

        connectionManager.prepareAndExecuteUpdate(channelTableQuery, null);
        connectionManager.prepareAndExecuteUpdate(eventTableQuery, null);

    }
    
    @Override
    public Stored<Channel> save(Channel channel)
      throws SQLException {
        
        final Stored<Channel> stored = new Stored<Channel>(channel);

        String channelQuery = "INSERT INTO Channel VALUES(?,?,?)";
        String[] channelData = new String[]{stored.identity.toString(), stored.version.toString(), channel.name};
        connectionManager.prepareAndExecuteUpdate(channelQuery, channelData);
        
        // Write the list of events
        final Maybe.Builder<SQLException> exception = Maybe.builder();
        final Mutable<Integer> ordinal = new Mutable<Integer>(0);
        channel.events.forEach(event -> {
            final String eventQuery = "INSERT INTO ChannelEvent VALUES(?,?,?)";
            final String[] eventData = new String[]{stored.identity.toString(), event.identity.toString(), ordinal.get().toString()};
            try { connectionManager.prepareAndExecuteUpdate(eventQuery, eventData); }
            catch (SQLException e) { exception.accept(e) ; }
            ordinal.accept(ordinal.get() + 1);
        });

        Util.throwMaybe(exception.getMaybe());
        return stored;
    }
    
    @Override
    public synchronized Stored<Channel> update(Stored<Channel> channel,
                                            Channel new_channel)
        throws UpdatedException,
            DeletedException,
            SQLException {
        final Stored<Channel> current = get(channel.identity);
        final Stored<Channel> updated = current.newVersion(new_channel);
        if(current.version.equals(channel.version)) {
            String updateChannelQuery = "UPDATE Channel SET (version,name) =(?,?) WHERE id=?";
            String[] updateChannelData = new String[]{updated.version.toString(), new_channel.name, updated.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(updateChannelQuery, updateChannelData);


            // Rewrite the list of events
            String deleteEventQuery = "DELETE FROM ChannelEvent WHERE channel=?";
            String[] deleteEventData = new String[]{channel.identity.toString()};
           connectionManager.prepareAndExecuteUpdate(deleteEventQuery, deleteEventData);
            
            final Maybe.Builder<SQLException> exception = Maybe.builder();
            final Mutable<Integer> ordinal = new Mutable<Integer>(0);
            new_channel.events.forEach(event -> {
                final String insertEventQuery = "INSERT INTO ChannelEvent VALUES(?,?,?)";
                final String[] insertEventData = new String[]{channel.identity.toString(), event.identity.toString(), ordinal.get().toString()};

                try { connectionManager.prepareAndExecuteUpdate(insertEventQuery, insertEventData); }
                catch (SQLException e) { exception.accept(e) ; }
                ordinal.accept(ordinal.get() + 1);
            });

            Util.throwMaybe(exception.getMaybe());
        } else {
            throw new UpdatedException(current);
        }
        giveNextVersion(updated);
        return updated;
    }
   
    @Override
    public synchronized void delete(Stored<Channel> channel)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<Channel> current = get(channel.identity);
        if(current.version.equals(channel.version)) {
            String deleteChannelQuery = "DELETE FROM Channel WHERE id =?";
            String[] deleteChannelData = new String[]{channel.identity.toString()};
            connectionManager.prepareAndExecuteUpdate(deleteChannelQuery, deleteChannelData);
        } else {
        throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<Channel> get(UUID id)
      throws DeletedException,
             SQLException {

        final String channelQuery = "SELECT version,name FROM Channel WHERE id = ?";
        final String eventQuery = "SELECT event,ordinal FROM ChannelEvent WHERE channel = ? ORDER BY ordinal DESC";
        final String[] data = new String[]{id.toString()};

        final ResultSet channelResult = connectionManager.prepareAndExecuteQuery(channelQuery, data);
        final ResultSet eventResult = connectionManager.prepareAndExecuteQuery(eventQuery, data);

        if(channelResult.next()) {
            final UUID version = 
                UUID.fromString(channelResult.getString("version"));
            final String name =
                channelResult.getString("name");

            // Get all the events associated with this channel
            final List.Builder<Stored<Channel.Event>> events = List.builder();
            while(eventResult.next()) {
                final UUID eventId = UUID.fromString(eventResult.getString("event"));
                events.accept(eventStore.get(eventId));
            }
            return (new Stored<Channel>(new Channel(name,events.getList()),id,version));
        } else {
            throw new DeletedException();
        }
    }
    
    /**
     * This function creates a "dummy" update.
     * This function should be called when events are changed or
     * deleted from the channel.
     */
    public Stored<Channel> noChangeUpdate(UUID channelId)
        throws SQLException, DeletedException {
        String updateChannelQuery =  "UPDATE Channel SET (version) = (?) WHERE id= ?";
        String[] updateChannelData = new String[]{UUID.randomUUID().toString(), channelId.toString()};
        connectionManager.prepareAndExecuteUpdate(updateChannelQuery, updateChannelData);

        Stored<Channel> channel = get(channelId);
        giveNextVersion(channel);
        return channel;
    }
    
    /**
     * Get the current version UUID for the specified channel.
     * @param id UUID for the channel.
     */
    public UUID getCurrentVersion(UUID id)
      throws DeletedException,
             SQLException {

        final String channelQuery = "SELECT version FROM Channel WHERE id = ?";
        final String[] channelData = new String[]{id.toString()};
        final ResultSet channelResult = connectionManager.prepareAndExecuteQuery(channelQuery, channelData);

        if(channelResult.next()) {
            return UUID.fromString(
                    channelResult.getString("version"));
        }
        throw new DeletedException();
    }
    
    /**
     * Wait for a new version of a channel.
     * This is a blocking call to get the next version of a channel.
     * @param identity The identity of the channel.
     * @param version  The previous version accessed.
     * @return The newest version after the specified one.
     */
    public Stored<Channel> waitNextVersion(UUID identity, UUID version)
      throws DeletedException,
             SQLException {
        Maybe.Builder<Stored<Channel>> result
            = Maybe.builder();
        // Insert our result consumer
        synchronized(waiters) {
            Maybe<List<Consumer<Stored<Channel>>>> channelWaiters 
                = Maybe.just(waiters.get(identity));
            waiters.put(identity,List.cons(result,channelWaiters.defaultValue(List.empty())));
        }
        // Test if there already is a new version avaiable
        if(!getCurrentVersion(identity).equals( version)) {
            return get(identity);
        }
        // Wait
        synchronized(result) {
            while(true) {
                try {
                    result.wait();
                    return result.getMaybe().get();
                } catch (InterruptedException e) {
                    System.err.println("Thread interrupted.");
                } catch (Maybe.NothingException e) {
                    // Still no result, looping
                }
            }
        }
    }
    
    /**
     * Notify all waiters of a new version
     */
    private void giveNextVersion(Stored<Channel> channel) {
        synchronized(waiters) {
            Maybe<List<Consumer<Stored<Channel>>>> channelWaiters 
                = Maybe.just(waiters.get(channel.identity));
            try {
                channelWaiters.get().forEach(w -> {
                    w.accept(channel);
                    synchronized(w) {
                        w.notifyAll();
                    }
                });
            } catch (Maybe.NothingException e) {
                // No were waiting for us :'(
            }
            waiters.put(channel.identity,List.empty());
        }
    }
    
    /**
     * Get the channel belonging to a specific event.
     */
    public Stored<Channel> lookupChannelForEvent(Stored<Channel.Event> e)
      throws SQLException, DeletedException {
        String channelQuery = "SELECT channel FROM ChannelEvent WHERE event='" + e.identity + "'";
        String[] channelData = new String[]{e.identity.toString()};

        final ResultSet rs = connectionManager.prepareAndExecuteQuery(channelQuery, channelData);
        if(rs.next()) {
            final UUID channelId = UUID.fromString(rs.getString("channel"));
            return get(channelId);
        }
        throw new DeletedException();
    }

    public String getMessageOwner(UUID messageId) throws SQLException {
        String messageQuery = "SELECT * FROM Message WHERE id = ?;";
        String[] messageData = new String[]{messageId.toString()};

        final ResultSet rs = connectionManager.prepareAndExecuteQuery(messageQuery, messageData);
        return rs.getString("sender");
    }
} 
 
 
