package inf226.inchat;

import inf226.storage.*;
import inf226.util.Maybe;
import inf226.util.Util;

import java.util.UUID;
import java.time.Instant;
import java.sql.SQLException;
import inf226.util.immutable.List;

/**
 * This class models the chat logic.
 *
 * It provides an abstract interface to
 * usual chat server actions.
 *
 **/

public class InChat {
    private final UserStorage userStore;
    private final ChannelStorage channelStore;
    private final AccountStorage accountStore;
    private final SessionStorage sessionStore;

    public InChat(UserStorage userStore,
                  ChannelStorage channelStore,
                  AccountStorage accountStore,
                  SessionStorage sessionStore) {
        this.userStore=userStore;
        this.channelStore=channelStore;
        this.accountStore=accountStore;
        this.sessionStore=sessionStore;
    }


    /**
     * Log in a user to the chat.
     */
    public Maybe<Stored<Session>> login(String username, String password) {
        try {
            final String pwd = accountStore.getPassword(username);
            //Checks is the passwords matches the password in the database
            if(Password.check(password,pwd)) {
                final Stored<Account> account = accountStore.lookup(username);

                final Stored<Session> session = sessionStore.save(new Session(account, Instant.now().plusSeconds(60 * 60 * 24)));
                return Maybe.just(session);
            }
            else{
                return Maybe.nothing();
            }
        } catch (SQLException | DeletedException e) {
            return Maybe.nothing();
        }
    }
    
    /**
     * Register a new user.
     */
    public Maybe<Stored<Session>> register(String username, String password, String pass_repeat) {
        try {
            //Checks if the passwords fulfills the NIST requirements
            if (Password.verifyPassword(password) && password.contentEquals(pass_repeat)) {

                final Stored<User> user = userStore.save(User.create(username));

                final Stored<Account> account = accountStore.save(Account.create(user, password));

                final Stored<Session> session = sessionStore.save(new Session(account, Instant.now().plusSeconds(60 * 60 * 24)));

                return Maybe.just(session);
            }
            return Maybe.nothing();
        } catch (SQLException e) {
            return Maybe.nothing();
        }
    }
    
    /**
     * Restore a previous session.
     */
    public Maybe<Stored<Session>> restoreSession(UUID sessionId) {
        //create session token with hash of session id?
        try {
            return Maybe.just(sessionStore.get(sessionId));
        } catch (SQLException e) {
            System.err.println("When restoring session:" + e);
            return Maybe.nothing();
        } catch (DeletedException e) {
            return Maybe.nothing();
        }
    }
    
    /**
     * Log out and invalidate the session.
     */
    public void logout(Stored<Session> session) {
        try {
            Util.deleteSingle(session,sessionStore);
        } catch (SQLException e) {
            System.err.println("When loging out of session:" + e);
        }
    }
    
    /**
     * Create a new channel.
     */
    public Maybe<Stored<Channel>> createChannel(Stored<Account> account,
                                                String name) {
        //check cookie for token and create channel, else, error.
        try {
            Stored<Channel> channel
                = channelStore.save(new Channel(name,List.empty()));
            accountStore.setUserAccess(account,channel,"owner");
            return joinChannel(account, channel.identity);
        } catch (SQLException e) {
            System.err.println("When trying to create channel " + name +":\n" + e);
        }
        return Maybe.nothing();
    }
    
    /**
     * Join a channel.
     */
    public Maybe<Stored<Channel>> joinChannel(Stored<Account> account,
                                              UUID channelID) {
        //maybe check token
        try {
            Stored<Channel> channel = channelStore.get(channelID);
            if(!accountStore.checkUserAccess(account,channel)){
                accountStore.setUserAccess(account,channel,"participant");
            }
            Util.updateSingle(account,
                    accountStore,
                    a -> a.value.joinChannel(channel.value.name, channel));
            Stored<Channel.Event> joinEvent
                    = channelStore.eventStore.save(
                    Channel.Event.createJoinEvent(Instant.now(),
                            account.value.user.identity.toString()));
            return Maybe.just(
                    Util.updateSingle(channel,
                            channelStore,
                            c -> c.value.postEvent(joinEvent)));
        } catch (DeletedException e) {
            // This channel has been deleted.
        } catch (SQLException e) {
            System.err.println("When trying to join " + channelID +":\n" + e);
        }
        return Maybe.nothing();
    }
    
    /**
     * Post a message to a channel.
     */
    public Maybe<Stored<Channel>> postMessage(Stored<Account> account,
                                              Stored<Channel> channel,
                                              String message) {

        //check cookie token and post if valid, else error.
            try {
                Stored<Channel.Event> event
                        = channelStore.eventStore.save(
                        Channel.Event.createMessageEvent(Instant.now(),
                                account.value.user.identity.toString(), message));
                try {
                    return Maybe.just(
                            Util.updateSingle(channel,
                                    channelStore,
                                    c -> c.value.postEvent(event)));
                } catch (DeletedException e) {
                    // Channel was already deleted.
                    // Let us pretend this never happened
                    Util.deleteSingle(event, channelStore.eventStore);
                }
            } catch (SQLException e) {
                System.err.println("When trying to post message in " + channel.identity + ":\n" + e);
            }
            return Maybe.nothing();
        }
    
    /**
     * A blocking call which returns the next state of the channel.
     */
    public Maybe<Stored<Channel>> waitNextChannelVersion(UUID identity, UUID version) {
        try {
            return Maybe.just(channelStore.waitNextVersion(identity, version));
        } catch (SQLException e) {
            System.err.println("While waiting for the next message in " + identity +":\n" + e);
        } catch (DeletedException e) {
            // Channel deleted.
        }
        return Maybe.nothing();
    }
    
    /**
     * Get an event by its identity.
     */
    public Maybe<Stored<Channel.Event>> getEvent(UUID eventID) {
        try {
            return Maybe.just(channelStore.eventStore.get(eventID));
        } catch (SQLException | DeletedException e) {
            return Maybe.nothing();
        }
    }
    
    /**
     * Delete an event.
     */
    public Stored<Channel> deleteEvent(Stored<Channel> channel, Stored<Channel.Event> event) {
        //maybe check token
        try {
            Util.deleteSingle(event , channelStore.eventStore);
            return channelStore.noChangeUpdate(channel.identity);
        } catch (SQLException er) {
            System.err.println("While deleting event " + event.identity +":\n" + er);
        } catch (DeletedException er) {
            er.printStackTrace();
        }
        return channel;
    }

    /**
     * Edit a message.
     */
    public Stored<Channel> editMessage(Stored<Channel> channel,
                                       Stored<Channel.Event> event,
                                       String newMessage) {
        //check token
        try{
            Util.updateSingle(event,
                            channelStore.eventStore,
                            e -> e.value.setMessage(newMessage));
            return channelStore.noChangeUpdate(channel.identity);
        } catch (SQLException er) {
            System.err.println("While deleting event " + event.identity +":\n" + er);
        } catch (DeletedException er) {
            er.printStackTrace();
        }
        return channel;
    }

    public void setUserAccess(String Username, Stored<Channel> channel, String role) throws SQLException, DeletedException {
        Stored<Account> account = accountStore.lookup(Username);

        if (accountStore.getUserAccess(account, channel.identity).equals("owner")) {
            System.err.println("Can't change role of owner to make sure that every channel has at least one owner");
            return;
        }
        if (accountStore.checkUserAccess(account,channel)){
            accountStore.updateUserAccess(account,channel,role);
        }else{
            accountStore.setUserAccess(account,channel,role);
        }
    }

    public String getUserAccess(Stored<Account> account,UUID channel) throws SQLException {
        return accountStore.getUserAccess(account,channel);
    }

    /**
     * gets the user that ownes the messages.
     */
    public String getMessageOwner(UUID messageId) throws SQLException, DeletedException {
        return accountStore.lookup(getUserName(channelStore.getMessageOwner(messageId))).identity.toString();
    }

    public String getUserName(String UserID){
        String UserName = "";
        try {
            UserName = accountStore.getUserName(UserID);
        }catch (SQLException e){
            e.printStackTrace();
        }
        return UserName;
    }
}


