package inf226.inchat;

import java.io.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import java.io.IOException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import java.util.TreeMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.lang.IllegalArgumentException;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;
import org.owasp.encoder.*;

import inf226.storage.*;
import inf226.util.*;


/**
 * The Handler class handles all HTTP and HTML components.
 * Functions called display⋯ and print⋯ output HTML.
 */

public class Handler extends AbstractHandler
{
  // Static resources:
  private final File style = new File("style.css");
  private final File login = new File("login.html");
  private final File register = new File("register.html");
  private final File landingpage = new File("index.html");
  private final File script = new File("script.js");

  private static InChat inchat;
  
  private final DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (z)")
                                 .withZone( ZoneId.systemDefault() );

  // Final strings that are used to check access, etc
  private final String SESSION = "session";
  private final String OWNER = "owner";
  private final String PARTICIPANT = "participant";
  private final String MODERATOR = "moderator";
  private final String OBSERVER = "observer";
  private final String BANNED = "banned";

  
  /**
   * This is the entry point for HTTP requests.
   * Some requests require login, while some can be processed
   * without a valid session.
   */
  public void handle(String target,
                     Request baseRequest,
                     HttpServletRequest request,
                     HttpServletResponse response)
    throws IOException
  {
    System.err.println("Got a request for \"" + Encode.forJava(target) + "\"");
    final Map<String,Cookie> cookies = getCookies(request);

    // Pages which do not require login
      switch (target) {
          case "/style.css":
              serveFile(response, style, "text/css;charset=utf-8");
              baseRequest.setHandled(true);
              return;
          case "/login":
              serveFile(response, login, "text/html;charset=utf-8");
              baseRequest.setHandled(true);
              return;
          case "/register":
              serveFile(response, register, "text/html;charset=utf-8");
              baseRequest.setHandled(true);
              return;
          case "/script.js":
              serveFile(response, script, "application/javascript");
              baseRequest.setHandled(true);
              return;
      }
    
    // Attempt to create a session
    
    Maybe.Builder<Stored<Session>> sessionBuilder
        = new Maybe.Builder<Stored<Session>>();
        
    if(request.getParameter("register") != null) {
        // Try to register a new user:
        System.err.println("User registration.");
        try {
            String username = (new Maybe<String>
                (request.getParameter("username"))).get();
            String password = (new Maybe<String>
                (request.getParameter("password"))).get();
            String password_repeat = (new Maybe<String>
                    (request.getParameter("password_repeat"))).get();
            System.err.println("Registering user: \"" + Encode.forJava(username)
                             + "\" with password \"" + Encode.forJava(password) + "\"");

            inchat.register(username,password,password_repeat).forEach(sessionBuilder);

        } catch (Maybe.NothingException e) {
            // Not enough data suppied for login
            System.err.println("Broken usage of register");
        }
    } else if(request.getParameter("login") != null) {
        // Login for an existing user
        System.err.println("Trying to log in as:");
        try {
            final String username = (new Maybe<String>
                (request.getParameter("username"))).get();
            System.err.println("Username: " + Encode.forJava(username));
            final String password = (new Maybe<String>
                (request.getParameter("password"))).get();
            inchat.login(username,password).forEach(sessionBuilder);
        } catch (Maybe.NothingException e) {
            // Not enough data suppied for login
            System.err.println("Broken usage of login");
        }
    
    } else {
        // Final option is to restore a session from a cookie
        final Maybe<Cookie> sessionCookie
            = new Maybe<Cookie>(cookies.get(SESSION));
        final Maybe<Stored<Session>> cookieSession
            =  sessionCookie.bind(c -> 
                    inchat.restoreSession(UUID.fromString(c.getValue())));
        cookieSession.forEach(sessionBuilder);
        
    }
    response.setContentType("text/html;charset=utf-8");
    
    try {
        final Stored<Session> session = sessionBuilder.getMaybe().get();
        final Stored<Account> account = session.value.account;
        // User is now logged in with a valid session.
        // We set the session cookie to keep the user logged in:


        //This is the session cookie is sett
        Cookie cookie = new Cookie(SESSION, session.identity.toString());

        //I would setSecure(true) but since not all browsers allow it, i have commented it out. but usually i would
        //cookie.setSecure(true);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        final PrintWriter out = response.getWriter();
        // Handle a logged in request.
        try {
            if(target.startsWith("/channel/")) {
                String errorMessage = "";
                final String alias
                    = target.substring(("/channel/").length());
                
                // Resolve channel within the current session
                Stored<Channel> channel =
                    Util.lookup(account.value.channels,alias).get();

                // This is where banned users are filtered out
                // They are not sent to the channel page, but to a page that tells them they are banned
                try {
                    if (inchat.getUserAccess(account, channel.identity).contains(BANNED)) {
                        out.println("<!DOCTYPE html>");
                        out.println("<html lang=\"en-GB\">");
                        out.println("<h1 style=\"color:red\">ACCESS DENIED! </h1>");
                        out.println("<div>You are banned from channel " + Encode.forHtml(alias) + "</div>");
                        out.println("</html>");
                        response.setStatus(HttpServletResponse.SC_OK);
                        baseRequest.setHandled(true);
                        return ;
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }


                if(request.getMethod().equals("POST")) {
                    // This is a request to post something in the channel.

                    if(request.getParameter("newmessage") != null) {
                        // Checking the csrf token
                        if (!checkCSRFToken(request, response, session.identity.toString())) return;

                        String message = (new Maybe<String>
                            (request.getParameter("message"))).get();

                        try {

                            if (inchat.getUserAccess(account,channel.identity).contains(OWNER) ||
                                    inchat.getUserAccess(account,channel.identity).contains(MODERATOR) ||
                                    inchat.getUserAccess(account,channel.identity).contains(PARTICIPANT)){

                                channel = inchat.postMessage(account, channel, message).get();
                            }
                            else { // Letting the user know what happened
                                errorMessage = "You don't have access to post messages";
                            }
                        } catch (SQLException throwables) {
                            throwables.printStackTrace();
                        }
                    }

                    
                    if(request.getParameter("deletemessage") != null) {
                        try{
                            UUID messageId =
                                    UUID.fromString(Maybe.just(request.getParameter("message")).get());

                            // Only owners, moderators and the author of a message can delete this message
                            if(inchat.getUserAccess(account,channel.identity).contains(OWNER) ||
                                    inchat.getUserAccess(account,channel.identity).contains(MODERATOR) ||
                                    account.identity.toString().contains(inchat.getMessageOwner(messageId))) {

                                Stored<Channel.Event> message = inchat.getEvent(messageId).get();

                                channel = inchat.deleteEvent(channel, message);
                            }
                            else {
                                errorMessage = "You don't have access to delete this message";
                            }
                        }catch (SQLException | DeletedException e){
                            e.printStackTrace();
                        }

                    }
                    if(request.getParameter("editmessage") != null) {
                        if (!checkCSRFToken(request, response, session.identity.toString())) return;

                        try{

                            String message = (new Maybe<String>
                                    (request.getParameter("content"))).get();
                            UUID messageId =
                                    UUID.fromString(Maybe.just(request.getParameter("message")).get());

                            if(inchat.getUserAccess(account,channel.identity).contains(OWNER) ||
                                    inchat.getUserAccess(account,channel.identity).contains(MODERATOR) ||
                                    account.identity.toString().contains(inchat.getMessageOwner(messageId))) {
                                Stored<Channel.Event> event = inchat.getEvent(messageId).get();
                                channel = inchat.editMessage(channel, event, message);
                            }
                            else {
                                errorMessage = "You don't have access to edit this message";
                            }

                        }catch (SQLException | DeletedException e){
                            e.printStackTrace();
                        }
                    }
                    if(request.getParameter("setpermission") != null){
                        String role = request.getParameter("role");
                        String Username = request.getParameter("username");
                        try {
                            if (inchat.getUserAccess(account, channel.identity).contains(OWNER)) {
                                inchat.setUserAccess(Username, channel, role);
                            }
                            else {
                                errorMessage = "You don't have access to change roles";
                                System.err.println("Only the owner can change roles");
                            }
                        } catch (SQLException | DeletedException throwables) {
                            throwables.printStackTrace();
                        }

                    }
                    
                }
                
                out.println("<!DOCTYPE html>");
                out.println("<html lang=\"en-GB\">");
                out.println("<div style=\"color: red;margin-bottom: 15px;\">" + errorMessage + "</div>");
                printStandardHead(out, "inChat: " + alias);
                out.println("<body>");printStandardTop(out,  "inChat: " + alias);
                out.println("<div class=\"main\">");
                printChannelList(out, account.value, alias);
                printChannel(out, channel, alias, request, account);
                out.println("</div>");
                out.println("</body>");
                out.println("</html>");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                return ;
            }
            
            if(target.startsWith("/create")) {
                out.println("<!DOCTYPE html>");
                out.println("<html lang=\"en-GB\">");
                printStandardHead(out, "inChat: Create a new channel!");
                out.println("<body>");
                printStandardTop(out,  "inChat: Create a new channel!");
                
                out.println("<form class=\"login\" action=\"/\" method=\"POST\">"
                  + "<div class=\"name\"><input type=\"text\" name=\"channelname\" placeholder=\"Channel name\"></div>"
                  + "<div class=\"submit\"><input type=\"submit\" name=\"createchannel\" value=\"Create Channel\"></div>"
                        + "<input type=\"hidden\" name=\"csrf\" value=\"" + Encode.forHtml(session.identity.toString()) + "\"</input>" //csrf
                        + "</form>");
                out.println("</body>");
                out.println("</html>");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                return ;
                
            }
            if(target.equals("/joinChannel")) {
                out.println("<!DOCTYPE html>");
                out.println("<html lang=\"en-GB\">");
                printStandardHead(out, "inChat: " + account.value.user.value.name.getUserName());
                out.println("<body>");
                printStandardTop(out, "inChat – Join a channel!");
                
                out.println("<form class=\"login\" action=\"/join\" method=\"POST\">"
                  + "<div class=\"name\"><input type=\"text\" name=\"channelid\" placeholder=\"Channel ID number:\"></div>"
                  + "<div class=\"submit\"><input type=\"submit\" name=\"joinchannel\" value=\"Join channel\"></div>"
                        + "<input type=\"hidden\" name=\"csrf\" value=\"" + Encode.forHtml(session.identity.toString()) + "\"</input>" //csrf
                  + "</form>");
                out.println("</body>");
                out.println("</html>");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                return ;
            }
            if(target.startsWith("/editMessage")) {
                String alias = (new Maybe<String>
                        (request.getParameter("channelname"))).get();
                String messageid = (new Maybe<String>
                        (request.getParameter("message"))).get();
                String originalContent = (new Maybe<String>
                        (request.getParameter("originalcontent"))).get();
                out.println("<!DOCTYPE html>");
                out.println("<html lang=\"en-GB\">");
                printStandardHead(out, "inChat: Edit message");
                out.println("<body>");
                printStandardTop(out,  "inChat: Edit message");
                out.println("<script src=\"/script.js\"></script>");
                
                out.println("<form class=\"entry\" action=\"/channel/" + Encode.forHtml(alias) + "\" method=\"post\">");
                out.println("  <div class=\"user\">You</div>");
                out.println("  <input type=\"hidden\" name=\"editmessage\" value=\"Edit\">");
                out.println("  <input type=\"hidden\" name=\"message\" value=\"" + Encode.forHtml(messageid) + "\">");
                out.println("  <textarea id=\"messageInput\" class=\"messagebox\" placeholder=\"Post a message in this channel!\" name=\"content\">" + originalContent + "</textarea>");
                out.println("  <div class=\"controls\"><input style=\"float: right;\" type=\"submit\" name=\"edit\" value=\"Edit\"></div>");
                out.println("  <input type=\"hidden\" name=\"csrf\" value=\""+ Encode.forHtml(session.identity.toString()) + "\"</input>"); //csrf
                out.println("</form>");
                out.println("<script>");
                out.println("let msginput = document.getElementById(\"messageInput\");");
                out.println("msginput.focus()");
                out.println("msginput.addEventListener(\"keypress\", submitOnEnter);");
                out.println("</script>");
            
                out.println("</body>");
                out.println("</html>");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                return ;
                
            }

            if (target.startsWith("/join")) {
                try {
                    final Maybe<String> idparam
                            = Maybe.just(request.getParameter("channelid"));
                    final UUID channelId
                            = UUID.fromString(idparam.get());
                    Stored<Channel> channel
                            = inchat.joinChannel(account, channelId).get();
                    response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                    response.setHeader("Location", "/channel/" + channel.value.name);
                    baseRequest.setHandled(true);
                    return;

                } catch (IllegalArgumentException e) {
                    // Not a valid UUID request a new one
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println("Invalid UUID");
                    baseRequest.setHandled(true);
                    return;

                } catch (Maybe.NothingException e) {
                    // Joining failed.
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.println("Failed to join channel.");
                    baseRequest.setHandled(true);
                    return;
                }
            }

            if(target.startsWith("/logout")) {
                inchat.logout(session);
                response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                response.setHeader("Location","/");
                baseRequest.setHandled(true);
                return;
            }
            
            if(target.startsWith("/subscribe/")) {
                System.err.println("Got a subscribe request.");
                UUID version = 
                    UUID.fromString(Maybe.just(request.getParameter("version")).get());
                UUID identity =
                    UUID.fromString(target.substring(("/subscribe/").length()));
                Stored<Channel> channel = inchat.waitNextChannelVersion(identity,version).get();
                System.err.println("Got a new version.");
                out.println(channel.version);
                printChannelEvents(out,channel,request, account);
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                return ;
                /*channel = inchat.subscribeChannel()*/
            }

            if(request.getParameter("createchannel") != null) {
                // Try to create a new channel
                System.err.println("Channel creation.");
                if (!checkCSRFToken(request, response, session.identity.toString())) return;
                try {
                    String channelName = (new Maybe<String>
                        (request.getParameter("channelname"))).get();
                                        
                    Stored<Channel> channel 
                        = inchat.createChannel(account,channelName).get();
                    
                    // Redirect to the new channel
                    response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                    response.setHeader("Location","/channel/" + channel.value.name);
                    baseRequest.setHandled(true);
                    return;
                } catch (Maybe.NothingException e) {
                    System.err.println("Could not create channel.");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.println("Failed to create channel.");
                    baseRequest.setHandled(true);
                    return;
                }
            }
            
            
            if(target.equals("/")) {
                out.println("<!DOCTYPE html>");
                out.println("<html lang=\"en-GB\">");
                printStandardHead(out, "inChat: " + account.value.user.value.name.getUserName());
                out.println("<body>");
                printStandardTop(out, "inChat: " + account.value.user.value.name.getUserName());
                out.println("<div class=\"main\">");
                printChannelList(out, account.value, "");
                out.println("<div class=\"channel\">Hello!</div>");
                out.println("</div>");
                out.println("</body>");
                out.println("</html>");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
            }
        
        } catch (Maybe.NothingException e) {
            /* Something was not found, we let the handler pass through,
               Jetty will give them a 404. */
        }
    } catch (Maybe.NothingException e) {
        // All authentication methods failed
        
        if (target.equals("/")) {
            serveFile(response,landingpage, "text/html;charset=utf-8");
        } else {
            System.err.println("User was not logged in, redirect to login.");
            response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
            response.setHeader("Location", "/login");
        }
        baseRequest.setHandled(true);
    }
  }

  private boolean checkCSRFToken(HttpServletRequest request, HttpServletResponse response, String validToken) throws IOException {
      String csrfToken = request.getParameter("csrf");
      if (csrfToken == null) {
          response.sendError(403, "CSRF attack detected - no token");
          System.err.println("No cookie");
          return false;
      }
      if (!csrfToken.equals(validToken)) {
          response.sendError(403, "CSRF attack detected - wrong token");
          System.err.println("Wrong cookie");
          return false;
      }
      return true;
  }


    /**
     * Print the standard HTML-header for InChat.
     * @param out The output to write to.
     * @param title The title of the page.
     */
    private void printStandardHead(PrintWriter out, String title) {
        out.println("<head>");
        out.println("<meta charset=\"UTF-8\">");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=yes\">");
        out.println("<style type=\"text/css\">code{white-space: pre;}</style>");
        out.println("<link rel=\"stylesheet\" href=\"/style.css\">");
        
        out.println("<title>" + Encode.forHtml(title) + "</title>");
        out.println("</head>");
    }

    /**
     * Print the standard top with actions.
     */
    private void printStandardTop(PrintWriter out, String topic) {
        out.println("<h1 class=\"topic\"><a style=\"color: black;\" href=\"/\">"+ Encode.forHtml(topic) + "</a></h1>");
        out.println("<div class=\"actionbar\">");
        out.println("<a class=\"action\" href=\"/create\">Create a channel!</a>");
        out.println("<a class=\"action\" href=\"/joinChannel\">Join a channel!</a>");
        out.println("<a class=\"action\" href=\"/logout\">Logout</a>");
        out.println("</div>");
    }

    /**
     * Print a list of channesl for an account.
     */
    private void printChannelList(PrintWriter out, Account account, String current) {
        out.println("<aside class=\"chanlist\">");
        out.println("<p>Your channels:</p>");
        out.println("<ul class=\"chanlist\">");
        account.channels.forEach( entry -> {
            out.println("<li> <a href=\"/channel/" + Encode.forHtml(entry.first) + "\">" + Encode.forHtml(entry.first) + "</a></li>");
        });
        out.println("</ul>");
        out.println("</aside>");
    }
  
    /**
    * Render a channel as HTML
    **/
    private void printChannel(PrintWriter out,
                              Stored<Channel> channel,
                              String alias, HttpServletRequest request, Stored<Account> account) {
        
        out.println("<main id=\"channel\" role=\"main\" class=\"channel\">");
        printChannelEvents(out,channel,request, account);
        out.println("<script src=\"/script.js\"></script>");
        out.println("<script>subscribe(\"" + Encode.forJavaScript(channel.identity.toString()) +"\",\"" + Encode.forJavaScript(channel.version.toString()) + "\");</script>");

        try {
            if (!inchat.getUserAccess(account, channel.identity).contains(OBSERVER)) {
                out.println("<form class=\"entry\" action=\"/channel/" + Encode.forHtml(alias) + "\" method=\"post\">");
                out.println("  <div class=\"user\">You</div>");
                out.println("  <input type=\"hidden\" name=\"newmessage\" value=\"Send\">");
                out.println("  <textarea id=\"messageInput\" class=\"messagebox\" placeholder=\"Post a message in this channel!\" name=\"message\"></textarea>");
                out.println("  <div class=\"controls\"><input style=\"float: right;\" type=\"submit\" name=\"send\" value=\"Send\"></div>");
                out.println("  <input type=\"hidden\" name=\"csrf\" value=\"" + Encode.forHtml(getCookies(request).get(SESSION).getValue()) + "\"</input>"); //csrf
                out.println("</form>");
                out.println("<script>");
                out.println("let msginput = document.getElementById(\"messageInput\");");
                out.println("msginput.focus()");
                out.println("msginput.addEventListener(\"keypress\", submitOnEnter);");
                out.println("</script>");
            }
        } catch (SQLException er) {
            er.printStackTrace();
        }
        out.println("</main>");
        // Print out the aside:
        out.println("<aside class=\"chanmenu\">");
        out.println("<h4>Channel ID:</h4><br>" + Encode.forHtml(channel.identity.toString()) +"<br>");
        out.println("<p><a href=\"/join?channelid=" + Encode.forHtml(channel.identity.toString()) + "\">Join link</a></p>");

        out.println("<h4>Set permissions</h4><form action=\"/channel/" + Encode.forHtml(alias) + "\" method=\"post\">");
        out.println("<input style=\"width: 8em;\" type=\"text\" placeholder=\"User name\" name=\"username\">");
        out.println("<select name=\"role\" required=\"required\">");
        out.println("<option value=\"owner\">Owner</option>");
        out.println("<option value=\"moderator\">Moderator</option>");
        out.println("<option value=\"participant\">Participant</option>");
        out.println("<option value=\"observer\">Observer</option>");
        out.println("<option value=\"banned\">Banned</option>");
        out.println("<input type=\"submit\" name=\"setpermission\" value=\"Set!\">");
        out.println("<input type=\"hidden\" name=\"csrf\" value=\"" + Encode.forHtml(getCookies(request).get(SESSION).getValue()) + "\"</input>"); //csrf

        out.println("</select>");
        out.println("</form>");

        out.println("</aside>");
    }
    
    /**
     * Render the events of a channel as HTML.
     */
    private void printChannelEvents(PrintWriter out,
                              Stored<Channel> channel, HttpServletRequest request, Stored<Account> account) {
        out.println("<div id=\"chanevents\">");
        channel.value
               .events
               .reverse()
               .forEach(printEvent(out,channel,request, account));
        out.println("</div>");  
    }
    
    /**
     * Render an event as HTML.
     */
    private Consumer<Stored<Channel.Event>> printEvent(PrintWriter out, Stored<Channel> channel, HttpServletRequest request, Stored<Account> account) {
        return (e -> {
            switch(e.value.type) {
                case message:
                    out.println("<div class=\"entry\">");
                    out.println("    <div class=\"user\">" + Encode.forHtml(inchat.getUserName(e.value.sender)) + "</div>");
                    out.println("    <div class=\"text\">" + Encode.forHtml(e.value.message));
                    out.println("    </div>");

                    try {
                        if (inchat.getUserAccess(account, channel.identity).contains(OWNER) ||
                                inchat.getUserAccess(account, channel.identity).contains(MODERATOR) ||
                                account.identity.toString().contains(inchat.getMessageOwner(e.identity))) {
                            out.println("    <div class=\"messagecontrols\">");
                            out.println("        <form style=\"grid-area: delete;\" action=\"/channel/" + Encode.forHtml(channel.value.name) + "\" method=\"POST\">");
                            out.println("        <input type=\"hidden\" name=\"message\" value=\"" + Encode.forHtml(e.identity.toString()) + "\">");
                            out.println("        <input type=\"submit\" name=\"deletemessage\" value=\"Delete\">");
                            out.println("        </form><form style=\"grid-area: edit;\" action=\"/editMessage\" method=\"POST\">");
                            out.println("        ");
                            out.println("        <input type=\"hidden\" name=\"message\" value=\"" + Encode.forHtml(e.identity.toString()) + "\">");
                            out.println("        <input type=\"hidden\" name=\"channelname\" value=\"" + Encode.forHtml(channel.value.name) + "\">");
                            out.println("        <input type=\"hidden\" name=\"originalcontent\" value=\"" + Encode.forHtml(e.value.message) + "\">");
                            out.println("        <input type=\"submit\" name=\"editmessage\" value=\"Edit\">");
                            out.println("        <input type=\"hidden\" name=\"csrf\" value=\"" + Encode.forHtml(getCookies(request).get(SESSION).getValue()) + "\"</input>"); //csrf
                            out.println("        </form>");
                            out.println("    </div>");
                        }
                    }
                    catch (SQLException | DeletedException er) {
                        er.printStackTrace();
                    }
                    out.println("</div>");
                    return;
                case join:
                    out.println("<p>" + Encode.forHtml(formatter.format(e.value.time)) + " " + Encode.forHtml(inchat.getUserName(e.value.sender)) + " has joined!</p>");
            }
        });
    }

  /**
   * Load all the cookies into a map for easy retrieval.
   */
  private static Map<String,Cookie> getCookies (HttpServletRequest request) {
    final Map<String,Cookie> cookies = new TreeMap<String,Cookie>();
    final Cookie[] carray = request.getCookies();
    if(carray != null) {
        for (Cookie cookie : carray) {
            cookies.put(cookie.getName(), cookie);
        }
    }
    return cookies;
  }

  /**
   * Serve a static file from file system.
   */
  private void serveFile(HttpServletResponse response, File file, String contentType) {
      response.setContentType(contentType);
      try {
        final InputStream is = new FileInputStream(file);
        is.transferTo(response.getOutputStream());
        is.close();
        response.setStatus(HttpServletResponse.SC_OK);
      } catch (IOException e) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
  }

  /**
   * main function. Sets up the forum.
   */
  public static void main(String[] args) throws Exception
  {
  
    final String path = "production.db";
    final String dburl = "jdbc:sqlite:" + path;
    final Connection connection = DriverManager.getConnection(dburl);
    try{
        connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON");

        UserStorage userStore = new UserStorage(connection);

        EventStorage eventStore = new EventStorage(connection);

        ChannelStorage channelStore = new ChannelStorage(connection,eventStore);

        AccountStorage accountStore = new AccountStorage(connection,userStore,channelStore);

        SessionStorage sessionStore
            = new SessionStorage(connection,accountStore);
        inchat = new InChat(userStore,channelStore,accountStore,sessionStore);
        try {
            final Stored<Session> admin = inchat.register("admin","Pa$$w0rd","Pa$$w0rd").get();
            final Stored<Channel> debug = inchat.createChannel(admin.value.account, "debug").get();
            (new Thread(){ public void run() {
                Mutable<Stored<Channel>> chan = new Mutable<Stored<Channel>>(debug);
                while(true) {
                    inchat.waitNextChannelVersion(chan.get().identity, chan.get().version).forEach(chan);
                    chan.get().value.events.head().forEach( e -> {
                        try {
                        if(e.value.message != null) {
                            ResultSet rs = connection.createStatement().executeQuery(e.value.message);
                            if (rs.next()) {
                                inchat.postMessage(admin.value.account,chan.get(),rs.getString(1)).forEach(chan);
                            }
                        }
                        } catch(Exception re) {
                            re.printStackTrace();
                        }});
                }
            } }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        Server server = new Server(8081);
        server.setHandler(new Handler());
    
        server.start();
        server.join();
    } catch (SQLException e) {
       System.err.println("Inchat failed: " + Encode.forJava(e.toString()));
    }
    connection.close();
  }
}
