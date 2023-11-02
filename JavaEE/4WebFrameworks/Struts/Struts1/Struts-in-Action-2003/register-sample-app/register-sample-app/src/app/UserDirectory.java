package app;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Properties;

public class UserDirectory {

    /**
     *
     */
    private static final String UserDirectoryFile =
            "resources/users.properties";


    /**
     *
     */
    private static final String UserDirectoryHeader =
            "${user}=${password}";

    /**
     *
     */
    private static UserDirectory userDirectory = null;


    /**
     *
     */
    private static Properties p;


    /**
     *
     */
    private UserDirectory() throws UserDirectoryException {

        java.io.InputStream i = null;
        p = null;
        i = this.getClass().getClassLoader().
                getResourceAsStream(UserDirectoryFile);


        if (null==i) {
            throw new UserDirectoryException();
        }

        else {

            try {
                p = new Properties();
                p.load(i);
                i.close();
            }

            catch (java.io.IOException e) {
                p = null;
                System.out.println(e.getMessage());
                throw new UserDirectoryException();
            }

            finally {
                i = null;
            }

        } // end else

    } // end UserDirectory


    /**
     *
     */
    public static UserDirectory getInstance() throws
            UserDirectoryException {

        if (null==userDirectory) {

            userDirectory = new UserDirectory();

        }

        return userDirectory;

    }


    /**
     * Transform id so that it will match any conventions used by user
     * directory. The default implementation forces the id to
     * uppercase. Does <b>not</b> expect the userId to be null and
     * will throw a NPE if it is.
     *
     * @exception Throws Null Pointer Exception if userId is null.
     */
    public String fixId(String userId) {
        return userId.toUpperCase();
    }


    /**
     *
     */
    public boolean isValidPassword(String userId, String password) {

        // no null passwords
        if (null==password) return false;

        // conform userId to uppercase
        String _userId = fixId(userId);

        // no passwords for non-users
        if (!isUserExist(_userId)) return false;

        // does password match user's password
        return (password.equals(getPassword(_userId)));

    }


    /**
     *
     */
    public boolean isUserExist(String userId) {

        // no null users
        if (null==userId) return false;

        // if not null, it's a user
        return !(null==p.getProperty(userId));

    }


    /**
     *
     */
    public String getPassword(String userId)  {
        return p.getProperty(userId);
    }


    /**
     *
     */
    public Enumeration getUserIds()  {
        return p.propertyNames();
    }


    /**
     *
     */
    public void setUser(String userId, String password) throws
            UserDirectoryException {

        // no nulls
        if ((null==userId) || (null==password)) {
            throw new UserDirectoryException();
        }


        try {

            // conform userId to uppercase when stored
            p.put(fixId(userId), password);
            p.store(new FileOutputStream(UserDirectoryFile),
                    UserDirectoryHeader);

        }

        catch (IOException e) {

            throw new UserDirectoryException();

        }
    }
}
