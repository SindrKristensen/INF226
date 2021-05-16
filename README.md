# INCHAT – The INsecure CHAT application

![](maxresdefault.jpg)

Welcome to this second mandatory assignment of INF226.
In this assignment you will be improving the security
of a program called inChat – a very simple chat application,
in the shape of a [Jetty](https://www.eclipse.org/jetty/)
web application.

inChat has been especially crafted to contain a number
of security flaws. You can imagine that it has been
programmed by a less competent collegue, and that after
numerous securiy incidents, your organisation has decided
that you – a competent security professional – should take
some time to secure the app.

For your convenience, the task is separated into specific
exercises or tasks. These task might have been the result
of a security analysis. If you discover any security issues
beyond these tasks, you can make a note of them at the
end of this report.

For each task, you should make a short note how you solved
it – ideally with a reference to the relevant git-commits you
have made.

## Evaluation

This assignment is mandatory for the course, and counts 20%
of your final grade. The assigment is graded 0–20 points,
where you must get a minimum of 6 points in order to pass
the assignment.

## Groups

As with the previous assignments, you can work in groups of 1–3 students
on this assginment. Make sure that everyone is signed up for the group
on [MittUiB](https://mitt.uib.no/courses/24957/groups#tab-8746).
One good way to collaborate is that one person on the group makes a
fork and adds the other group members to that project.

## Getting and building the project

Log into [`git.app.uib.no`](https://git.app.uib.no/Hakon.Gylterud/inf226-2020-inchat) and make your
own fork of the project there. *Make sure your fork is private!*
You can then clone your repo to your own person machine.

To build the project you can use Maven on the command line, or configure
your IDE to use Maven to build the project. press ctrl twice to get run anything, and use the relevant commands;

 - `mvn compile` builds the project
 - `mvn test` runs the tests. (There are only a few unit test – feel free to add more).
 - `mvn exec:java` runs the web app.

Once the web-app is running, you can access it on [`localhost:8080`](http://localhost:8080/).

## Handing in the assignment

Before you hand in your assignment, make sure that you
have included all dependencies in the file `pom.xml`, and
that your program compiles and runs well. One good way
to test this is to make a fresh clone from the GitLab repo,
compile and test the app.

Once you are done, you submit the assignment on
[`mitt.uib.no`](https://mitt.uib.no/) as a link to your
fork – one link per group. This means you should not commit to the
repository after the deadline has passed. Include the commit hash
of the final commit (which you can find `git log`, for instance) in
your submission on MittUiB.

Remember to make your fork accessible to the TAs and lecturer. You can do
this from GitLab's menu, "Settings" → "Members".
Add the following people as developers:

 - Alba Gullerud,
 - Kenneth Fossen,
 - Jonas Møller,
 - Erlend Nærbø ,
 - Benjamin Chetioui, and
 - Håkon Gylterud

## Updates

Most likely the source code of the project will be updated by Håkon
while you are working on it. Therefore, it will be part of
your assignment to merge any new commits into your own branch.

## Improvements?

Have you found a non-security related bug?
Feel free to open an issue on the project GitLab page.
The best way is to make a separate `git branch` for these
changes, which do not contain your sulutions.

(This is ofcourse completely volountary – and not a graded
part of the assignment)

If you want to add your own features to the chat app - feel free
to do so! If you want to share them, contact Håkon and we can
incorporate them into the main repo.

## Tasks

The tasks below has been separated out, and marked with their *approximate* weight. Each task has a section called "Notes" where you can
write notes on how you have solved the task.

### Task 0 – Authentication (4 points)

The original authentication mechanisms of inChat was so insecure it
had to be removed immediately and all traces of the old passwords
have been purged from the database. Therefore, the code in
`inf226.inchat.Account`, which is supposed to check the password,
always returns `true`.

#### Task 0 – Part A

*Update the code to use a secure password authentication method – one
of the methods we have discussed in lecture.*

Any data you need to store for the password check can be kept in the `Account` class, with
appropriate updates to `storage.AccountStorage`. Remember that the `Account` class is *immutable*.
Any new field must be immutable and `final` as well.

**Hint**:

 - An implementation of `scrypt` is already included as a dependency in `pom.xml`.
   If you prefer to use `argon2`, make sure to include it as well.


### Task 0 – Part B

Create two new, immutable, classes `UserName` and `Password` in the
inf226.inchat package, and replace `String` with these
classes in User and Account classes and other places in
the application where it makes sense.

Decide on a set of password criteria which satisfies
the NIST requirements, and implement these as invariants
in the Password class, and check these upon registration.

### Task 0 – Part C

*While the session cookie is an unguessable UUID, you must set the
correct protection flags on the session cookie.*

#### Notes – task 0

Here you write your notes about how this task was performed.

          We stored the password as a string in the account class, and made the Password class static. 
          This is to make it easier to call the check function and validate function. Becuase we wanted to 
          check the password in the registration
          and this made that easier. We used Scrypt to do the encrypting and the checking of password match.
          We also changed the sql database so that it also saves the hashed password that it gets from the Password class. 
          We have also made a simple salt generator, but haven't used it, becuse we then need to save the salt in the 
          database as Plain text.
          This makes the login function easier to make. 
          UserName class only saves the name, nothing more than that.
          Found the cookie in handler and set the appropriat flags, but cant setSecure to true because of 
          a an error with the diffrent internet browsers
          

### Task 1 – SQL injection (4 points)

The SQL code is currently wildly concatenating strings, leaving
it wide open to injection attacks.

*Take measures to prevent SQL injection attacks on the application.*

#### Notes – task 1

Here you write your notes about how this task was performed.

        To prevent SQL injections we use prepared statements. These work by separating the query and the data. 
        First we send the query, then we send each data parameter.
 
        Since there were quite a lot of classes communicating with the database, we decided to make a ConnectionManager class
        that manages all interactions with the database. This way we have more control and it is easier to prevent mistakes. 
        The class manages both updates (where the database is updated and no response is given) and queries (where a response 
        is returned from the database to the connection manager, and then to the class that called the connection manager). 


### Task 2 – Cross-site scripting (4 points)

The user interface is generated in `inf226.inchat.Handler`. The current
implementation is returning a lot of user data without properly
escaping it for the context it is displayed (for instance HTML body).

*Take measures to prevent XSS attacks on inChat.*

**Hint**: In addition to the books and the lecture slides, you should
take a look at the [OWASP XSS prevention cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)

#### Notes – task 2
    
Here you write your notes about how this task was performed.
    
        To solve this task we used OWASPs library to encode vulnerable information that the server prints out, which prevents XSS.
        For data inside the HTML it is encoded with Encode.ForHtml, for data inside Scripts it is encoded with Encode.ForJavascript,
        and for data inside Java print-statements it is encoded with Encode.ForJava. 
        ForHtml and ForJavascript is more general than ForHtmlAttribute and ForJavascriptAttribute, meaning they will use a little 
        bit more bytes to process, but not so much it will make a difference for our small system.

### Task 3 – Cross-site request forgery (1 point)

While the code uses UUIDs to identify most objects, some
form actions are still susceptible to cross-site request forgery attacks
(for instance the `newmessage` and the `createchannel` actions.)

*Implement anti-CSRF tokens or otherwise prevent CSRF on
the vulnerable forms.*

**Hint:** it is OK to use the session cookie as such a token.

#### Notes – task 3

        At the start of a user session, the user is given a CSRF-token, based on the
        session identity. Every time a user makes a request, the server will check that the CSRF token
        is correct, and if not, the user will get an error screen (403).

        To implement this we added a CSRF-token in every "<form></form>". Then the CSRF-token of a request is checked when the request is recieved.
        
        It blocks CSRF, because a different site will have a different cookie.

### Task 4 – Access control (5 points)

inChat has no working access control. The channel side-bar has a form
to set roles for the channel,
but the actual functionality is not implemented.

In this task you should implement access control for inChat.

 - Identify which actions need access control, and decide
   on how you want to structure the access control.
 
Connect the user interface in the channel side-bar to your
access control system so that the security roles work as
intended. The security roles in a channel are:

 - *Owner*: Can set roles, delete and edit any message, as
   well as read and post to the channel.
 - *Moderator*: Can delete and edit any message, as
   well as read and post to the channel.
 - *Participant*: Can delete and edit their own messages, as
   well as read and post to the channel.
 - *Observer*: Can read messages in the channel.
 - *Banned*: Has no access to the channel.

The default behaviour should be that the creator of the
channel becomes the owner, and that inviting someone
puts them at the "Participant" level.
Also, make sure that your system satisfies the invariant:

 - Every channel has at least one owner.

 **Hint:** The InChat class is best suited to implement the
 access control checks since it in charge of all the operations
 on the chat. Implement a "setRole" method there, and add
 security checks to all other methods.

#### Notes – task 4

        We created a new database to store the roles and to which channel it is saved. 
        The way it is checked is through the UUID of the account and the UUID for the channel to get the role of the current
        user. When a user creates a channel he or she gets the owner-role by default, and the same when a diffrent user joins a channel
        he or she gets the participant-role by defult. And then to change a user permission you have to be an owner, and you
        can't change an owner's permission.

        When a user tries to enter a channel that he or she has been banned from they will be sent to he /channel/<channelname> path, but
        they wont be able to see any of the channel messages. This is implemented in the handler class by checking if the user has the role
        "banned", and if so we print some information and return.

### Task ω – Other security holes (2 points)

There are more security issues in this web application.
Improve the security of the application to the best of your
ability.

A note about HTTPS: We assume that inChat will be running
behind a reverse proxy which takes care of HTTPS, so you
can ignore issues related HTTPS.

#### Notes – task ω

        When a message is created, the Username of the creator is used to link the creator to the message. This means that
        if an attacker uses the same username as another user, he or she will be able to delete and edit all messages written by
        users with the same username. To avoid this security hole we switched to use the UUID of the creator instead so that this 
        would be unique for every user.

        Even though access control prevents participants and observers from deleting and editing other user's messages, the "edit"
        and "delete" buttons are still active and send a request when they are clicked. With normal users this is not a problem
        since the program filters out "illegal" requests when they arrive. But we think that it is not good to let an attacker
        know exactly what the delete and edit requests look like for messages he or she shouldn't have access to. This would give
        them an advantage when trying to hack inChat. 
        (This should also be done for the new message form for users with the observer role..)
        
        When you refresh the forum the last request that got sent gets resent (eg if your last action was to post a message, then 
        when you refresh the page this message will be posted again). This could be a problem if an attacker wants to flood a 
        channel or DoS the server.


