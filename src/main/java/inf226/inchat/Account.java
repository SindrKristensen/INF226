package inf226.inchat;
import inf226.util.immutable.List;
import inf226.util.Pair;


import inf226.storage.*;

/**
 * The Account class holds all information private to
 * a specific user.
 **/
public final class Account {
    /*
     * A channel consists of a User object of public account info,
     * and a list of channels which the user can post to.
     * user.
     */

    private final String password;
    public final Stored<User> user;
    public final List<Pair<String,Stored<Channel>>> channels;
    
    public Account(Stored<User> user, 
                   List<Pair<String,Stored<Channel>>> channels, String Hashedpassword) {
        this.user = user;
        this.channels = channels;
        this.password = Hashedpassword;

    }
    
    /**
     * Create a new Account.
     *
     * @param user The public User profile for this user.
     * @param password The login password for this account.
     **/
    public static Account create(Stored<User> user, String password) {

        password = Password.getPassword(password);

        return new Account(user,List.empty(), password);
    }
    
    /**
     * Join a channel with this account.
     *
     * @return A new account object with the channel added.
     */
    public Account joinChannel(String alias, Stored<Channel> channel) {
        Pair<String,Stored<Channel>> entry
            = new Pair<>(alias, channel);
        return new Account (user, List.cons(entry, channels),getPassword());
    }
    
    public String getPassword(){
        return password;
    }
}
