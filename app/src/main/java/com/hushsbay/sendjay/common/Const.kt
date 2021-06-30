package com.hushsbay.sendjay.common

class Const {

    enum class ServiceState {
        STOPPED, LOGOUTED, RUNNING
    }

    enum class SockState(num: Int) {
        BEFORE_CONNECT(0), FIRST_DISCONNECTED(2), RECONNECTED(4)
    }

    companion object {
        const val APP_NAME = "sendjay" //Do not change. see KeyChainProvider also
        const val TITLE = "sendjay" //for AlertDialog Title and so on
        const val VERSIONCHK_APP = "${APP_NAME}_and"
        const val VERSIONCHK_WEB = "webview"
        const val VERSIONCHK_ETC = "etc"
        ////////////////////////////////////////////////for distinguishing dev and ops (through KeyChain when app starts)
        const val SUFFIX_DEV = "devx" //for developer to check by userid if he want to connect to dev mode not ops(production) mode
        const val URL_HOST = "https://sendjay.com"
        const val DIR_PUBLIC = "/jay"
        const val DIR_ROUTE = "/route"
        ////////////////////////////////////////////////
        const val URL_SERVER = "$URL_HOST:444"
        const val URL_SOCK = "$URL_HOST:3051/jayops"
        const val URL_PUBLIC = URL_SERVER + DIR_PUBLIC
        //3050(web ops),3051(mobile ops) and 3060(web dev),3061(mobile dev)
        //These ports are for one server dev/ops. Consult with your team for load balancing of the real enterprise environment.
        //서버 1대로 개발과 운영을 나누어 관리하기 위한 포트이며 정상적인 로드밸런싱이 필요한 실제 기업 환경에서는 사내 담당자와의 협의가 필요할 것임.
        const val URL_SERVER_DEV = "$URL_HOST:454" //dev
        const val URL_SOCK_DEV = "$URL_HOST:3061/jaydev" //dev
        const val URL_PUBLIC_DEV = URL_SERVER_DEV + DIR_PUBLIC //dev
        ////////////////////////////////////////////////
        const val URL_JAY = "${DIR_PUBLIC}/" //means public page load
        const val URL_ROUTE = "${DIR_ROUTE}/" //means restful(=ajax)
        ////////////////////////////////////////////////
        const val PAGE_MAIN = "/index"
        const val PAGE_ROOM = "/chat"
        const val PAGE_DUMMY = "/dummy"
        ////////////////////////////////////////////////
        const val PROVIDER_AUTHORITY = "com.hushsbay.sendjay.common.KeyChainProvider" //same as ContentProvider class name and <provider ~ android:authorities in AndroidManifest.xml
        const val DELI = "##"
        const val SUBDELI = "$$"
        const val DELI_KEY = "__"
        const val W_KEY = "W$DELI_KEY"
        const val M_KEY = "M$DELI_KEY"
        const val NOTICHANID_FOREGROUND = "Uncheck this to hide icon."
        const val NOTICHANID_COMMON = "${Const.APP_NAME} notification"
        const val NOTI_ID_FOREGROUND_SERVICE = 1
        const val NOTI_CNT_START = 1001
        const val NOTI_CNT_END = 1010
        const val NOTI_ID_SUMMARY = NOTI_CNT_END + 1
        const val NOTI_ID_CHK_UNREAD = NOTI_ID_SUMMARY + 1
        /////////////////////////////////////////////////////////////////////////
        const val KC_MODE_SERVER = "mode_server" //see procLogin() in MainActivity
        const val KC_MODE_SOCK = "mode_sock" //see procLogin() in MainActivity
        const val KC_MODE_PUBLIC = "mode_public" //see procLogin() in MainActivity
        /////////////////////////////////////////////////////////////////////////
        const val KC_TOKEN = "token" //used for KeyChain, UserInfo and login.js
        const val KC_USERID = "userid" //used for KeyChain, UserInfo and login.js
        const val KC_USERKEY = "userkey" //used for KeyChain, UserInfo and login.js
        const val KC_USERNM = "usernm" //used for KeyChain, UserInfo and login.js
        const val KC_PASSKEY = "passkey" //used for KeyChain, UserInfo and login.js
        const val KC_ORGCD = "orgcd" //used for KeyChain, UserInfo and login.js
        const val KC_ROLE = "role" //used for KeyChain, UserInfo and login.js
        const val KC_AUTOLOGIN = "autologin" //used for KeyChain and UserInfo
        const val KC_SCREEN_STATE = "screen_state"
        const val KC_NOTI_OFF = "notioff" //used for KeyChain, UserInfo and login.js
        const val KC_SOUND_OFF = "soundoff" //used for KeyChain, UserInfo and login.js
        const val KC_TM_FR = "fr" //used for KeyChain, UserInfo and login.js
        const val KC_TM_TO = "to" //used for KeyChain, UserInfo and login.js
        const val KC_BODY_OFF = "bodyoff" //used for KeyChain, UserInfo and login.js
        const val KC_SENDER_OFF = "senderoff" //used for KeyChain, UserInfo and login.js
        const val KC_WINID = "winid"
        const val KC_USERIP = "userip"
        const val KC_PUSHTOKEN = "pushtoken"
        const val KC_ROOMID_FOR_CHATSERVICE = "roomid_for_chatservice"
        const val KC_WEBVIEW_MAIN_VERSION = "webview_main_version"
        const val KC_WEBVIEW_CHAT_VERSION = "webview_chat_version"
        const val KC_WEBVIEW_POPUP_VERSION = "webview_popup_version"
        /////////////////////////////////////////////////////////////////////////
        const val SOCK_EV_ALERT = "alert"
        const val SOCK_EV_TOAST = "toast"
        const val SOCK_EV_COMMON = "common"
        const val SOCK_EV_SET_ENV = "set_env"
        const val SOCK_EV_SEND_MSG = "send_msg"
        const val SOCK_EV_READ_MSG = "read_msg"
        const val SOCK_EV_RENAME_ROOM = "rename_room"
        const val SOCK_EV_CUT_MOBILE = "cut_mobile"
        const val SOCK_EV_MARK_AS_CONNECT = "mark_as_connect"
        /////////////////////////////////////////////////////////////////////////
        const val RESTFUL_TIMEOUT = 30000 //same as web client
        const val RESULT_OK = "0"
        const val RESULT_ERR = "-1"
        const val RESULT_ERR_HTTPFUEL = "-200"
        const val CELL_REVOKED = "message cancelled"
        const val CHECK_MOBILE_NETWORK = "$TITLE: No network available.\nCheck mobile network or wifi."
        const val WAIT_FOR_RECONNECT = "Please wait until network reconnected."
    }

}