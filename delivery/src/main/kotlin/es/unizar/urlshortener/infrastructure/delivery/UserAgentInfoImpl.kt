package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*


interface UserAgetInfo{
    fun getBrowser(userAgentHeader: String): String
    fun getOS(userAgentHeader: String): String
}
//https://gist.github.com/c0rp-aubakirov/a4349cbd187b33138969
class UserAgentInfoImpl() : UserAgetInfo{

    override fun getBrowser(userAgentHeader: String) : String{
        var uaLowCase = userAgentHeader.lowercase()
        var browser = ""
        if (uaLowCase.contains("msie")) {
            var substring = userAgentHeader.substring(userAgentHeader.indexOf("MSIE")).split(";")[0];
            browser = substring.split(" ")[0].replace("MSIE", "IE") + "-" + substring.split(" ")[1];
        } else if (uaLowCase.contains("safari") && uaLowCase.contains("version")) {
            browser = (userAgentHeader.substring(userAgentHeader.indexOf("Safari")).split(" ")[0]).split(
                "/")[0] + "-" + (userAgentHeader.substring(
                userAgentHeader.indexOf("Version")).split(" ")[0]).split("/")[1];
        } else if (uaLowCase.contains("opr") || uaLowCase.contains("opera")) {
            if (uaLowCase.contains("opera"))
                browser = (userAgentHeader.substring(userAgentHeader.indexOf("Opera")).split(" ")[0]).split(
                    "/")[0] + "-" + (userAgentHeader.substring(
                    userAgentHeader.indexOf("Version")).split(" ")[0]).split("/")[1];
            else if (uaLowCase.contains("opr"))
                browser = ((userAgentHeader.substring(userAgentHeader.indexOf("OPR")).split(" ")[0]).replace("/", "-")).replace(
                    "OPR", "Opera");
        } else if (uaLowCase.contains("chrome")) {
            browser = (userAgentHeader.substring(userAgentHeader.indexOf("Chrome")).split(" ")[0]).replace("/", "-");
        } else if ((uaLowCase.indexOf("mozilla/7.0") > -1) || (uaLowCase.indexOf("netscape6") != -1) || (uaLowCase.indexOf(
                "mozilla/4.7") != -1) || (uaLowCase.indexOf("mozilla/4.78") != -1) || (uaLowCase.indexOf(
                "mozilla/4.08") != -1) || (uaLowCase.indexOf("mozilla/3") != -1)) {
            //browser=(userAgent.substring(userAgent.indexOf("MSIE")).split(" ")[0]).replace("/", "-");
            browser = "Netscape-?";

        } else if (uaLowCase.contains("firefox")) {
            browser = (userAgentHeader.substring(userAgentHeader.indexOf("Firefox")).split(" ")[0]).replace("/", "-");
        } else if (uaLowCase.contains("rv")) {
            browser = "IE";
        } else {
            browser = "UnKnown, More-Info: " + userAgentHeader;
        }
        return browser
    }

    override fun getOS(userAgentHeader: String) : String{
        var uaLowCase = userAgentHeader.lowercase()
        var os = ""
        if (uaLowCase.contains("windows")) {
            os = "Windows"
        } else if (uaLowCase.contains("mac")) {
            os = "Mac"
        } else if (uaLowCase.contains("x11")) {
            os = "Unix"
        } else if (uaLowCase.contains("android")) {
            os = "Android"
        } else if (uaLowCase.contains("iphone")) {
            os = "IPhone"
        } else {
            os = "UnKnown, More-Info: " + userAgentHeader
        }
        return os
    }
}