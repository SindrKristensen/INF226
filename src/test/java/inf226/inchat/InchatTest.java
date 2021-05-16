 
package inf226.inchat;

import com.lambdaworks.crypto.SCryptUtil;
import org.junit.jupiter.api.Test;

import inf226.storage.*;

import inf226.util.*;

import javax.xml.transform.Result;
import java.security.Permission;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InchatTest{
    @Test
    void chatSetup() throws Maybe.NothingException,SQLException {
        UUID testID = UUID.randomUUID();
        System.err.println("Running test:" + testID);
        final String path = "test" + testID +  ".db";
        final String dburl = "jdbc:sqlite:" + path;
        final Connection connection = DriverManager.getConnection(dburl);
        connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");

        UserStorage userStore
            = new UserStorage(connection);

        EventStorage eventStore
            = new EventStorage(connection);

        ChannelStorage channelStore
            = new ChannelStorage(connection,eventStore);

        AccountStorage accountStore
            = new AccountStorage(connection,userStore,channelStore);

        SessionStorage sessionStore
            = new SessionStorage(connection,accountStore);

        InChat inchat = new InChat(userStore,channelStore,accountStore,sessionStore);

        Stored<Session> aliceSession = inchat.register("Alice","Badp1ss.word","Badp1ss.word").get();

        inchat.register("Bob","worsedE4g..","worsedE4g..").get();

        Stored<Session> bobSession = inchat.login("Bob","worsedE4g..").get();

        Stored<Channel> channel = inchat.createChannel(aliceSession.value.account,"Awesome").get();

        inchat.postMessage(aliceSession.value.account,channel, "Test message.").get();

        inchat.joinChannel(bobSession.value.account,channel.identity).get();
    }


    @Test
    void TestPassword() throws SQLException {
        final String path = "production.db";
        final String dburl = "jdbc:sqlite:" + path;
        final Connection connection = DriverManager.getConnection(dburl);
        try {
            connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");

            UserStorage userStore = new UserStorage(connection);

            EventStorage eventStore = new EventStorage(connection);

            ChannelStorage channelStore = new ChannelStorage(connection, eventStore);

            AccountStorage accountStore = new AccountStorage(connection, userStore, channelStore);

            if(Password.check("min", accountStore.getPassword("min"))){
                System.out.println(accountStore.getPassword("min"));
            }
        }catch (Exception e) {
        }
    }

    @Test
    void TestGoodPWD() throws Exception {
        String pwd = "Ha1234445..";
        assert Password.verifyPassword(pwd);
    }

    @Test
    void TestBadPWD() throws Exception {
        String pwd = "abcd";
        assert !Password.verifyPassword(pwd);
    }

    @Test
    void TestUser() throws SQLException {
        final String path = "production.db";
        final String dburl = "jdbc:sqlite:" + path;
        final Connection connection = DriverManager.getConnection(dburl);
        try {
            connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");

            UserStorage userStore = new UserStorage(connection);

            EventStorage eventStore = new EventStorage(connection);

            ChannelStorage channelStore = new ChannelStorage(connection, eventStore);

            AccountStorage accountStore = new AccountStorage(connection, userStore, channelStore);

            System.out.println(accountStore.lookup("Sindre").identity);

        }catch (Exception e) {
        }
    }

    @Test
    void TestSaltPWD() throws Exception {
        String pwd = "Ha1234445..";
        String salt = Password.getSalt();
        String hash_with_salt = Password.getPassword(pwd+salt);
        assert Password.check(pwd+salt, hash_with_salt);
    }

    @Test
    void TestPermission() throws SQLException, Maybe.NothingException, DeletedException {
        UUID testID = UUID.randomUUID();
        final String path = "test" + testID +  ".db";
        final String dburl = "jdbc:sqlite:" + path;
        final Connection connection = DriverManager.getConnection(dburl);
        connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");

        UserStorage userStore
                = new UserStorage(connection);

        EventStorage eventStore
                = new EventStorage(connection);

        ChannelStorage channelStore
                = new ChannelStorage(connection,eventStore);

        AccountStorage accountStore
                = new AccountStorage(connection,userStore,channelStore);

        SessionStorage sessionStore
                = new SessionStorage(connection,accountStore);

        InChat inchat = new InChat(userStore,channelStore,accountStore,sessionStore);

        Stored<Session> aliceSession = inchat.register("Alice","Badp1ss.word","Badp1ss.word").get();

        inchat.register("Bob","worsedE4g..","worsedE4g..").get();

        Stored<Session> bobSession = inchat.login("Bob","worsedE4g..").get();

        Stored<Channel> channel = inchat.createChannel(aliceSession.value.account,"Awesome").get();

        inchat.joinChannel(bobSession.value.account,channel.identity).get();

        assert accountStore.checkUserAccess(accountStore.lookup("Alice"),channel);
    }

}


