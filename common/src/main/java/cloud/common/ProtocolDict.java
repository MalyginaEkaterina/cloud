package cloud.common;

public class ProtocolDict {
    public static final short REGISTRATION = 1;
    public static final short REGISTRATION_STATUS = 2;
    public static final short AUTHORIZATION = 3;
    public static final short AUTHORIZATION_STATUS = 4;
    public static final short GET_DIR_STRUCTURE = 5;
    public static final short GET_DIR_STRUCTURE_STATUS = 6;
    public static final short CREATE_NEW_DIRECTORY = 7;
    public static final short CREATE_NEW_DIRECTORY_STATUS = 8;
    public static final short START_UPLOAD_FILE = 9;
    public static final short UPLOAD_FILE = 10;
    public static final short END_UPLOAD_FILE = 11;
    public static final short START_UPLOAD_FILE_STATUS = 12;
    public static final short END_UPLOAD_FILE_STATUS = 13;
    public static final short RENAME = 14;
    public static final short RENAME_STATUS = 15;
    public static final short STATUS_OK = 0;
    public static final short STATUS_ERROR = 1;
    public static final short STATUS_LOGIN_USED = 2;
    public static final short STATUS_LOGIN_FAIL = 3;
    public static final short STATUS_NOT_ENOUGH_MEM = 4;
    public static final short TYPE_DIRECTORY = 1;
    public static final short TYPE_FILE = 2;
}
