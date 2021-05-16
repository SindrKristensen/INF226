package inf226.inchat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ConnectionManager {

    final Connection connection;

    public ConnectionManager(Connection connection) {
        this.connection = connection;
    }

    /**
     * Method to prepare and execute query that should return a response in the form of a ResultSet
     * @param query The SQL query string with ? placeholders for data that should be inserted
     * @param data A list with the data to be inserted into the query in String form (the first element in the list wil substitute the first ? in the query
     * @return The result of the query from the database
     * @throws SQLException
     */
    public ResultSet prepareAndExecuteQuery(String query, String[] data) throws SQLException {
        PreparedStatement preparedStatement = prepareStatement(query, data);

        return preparedStatement.executeQuery();
    }

    /**
     * Method to prepare and execute an update that should not return a response
     * @param query The SQL query string with ? placeholders for data that should be inserted
     * @param data A list with the data to be inserted into the query in String form (the first element in the list wil substitute the first ? in the query
     * @throws SQLException
     */
    public void prepareAndExecuteUpdate(String query, String[] data) throws SQLException {
        PreparedStatement preparedStatement = prepareStatement(query, data);
        preparedStatement.executeUpdate();
    }

    /**
     * Private method to do the actual preparing of the statement and sending of the data parameters.
     * @param query The SQL query to be prepared
     * @param data The list of data parameters
     * @return The prepared statement with the query and the data parameters sent separately
     * @throws SQLException
     */
    private PreparedStatement prepareStatement(String query, String[] data) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(query);

        if (data != null) {
            for (int i = 0; i < data.length; i++) {

                preparedStatement.setString(i+1, data[i]);
            }
        }

        return preparedStatement;
    }

}
