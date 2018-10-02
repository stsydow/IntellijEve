package codegen

import java.util.regex.Pattern


/*
 * Function that checks whether the given string matches against the regular
 * expression that is provided by the Rust developers as given here:
 * https://doc.rust-lang.org/beta/reference/identifiers.html
 */
fun isValidRustIdentifier(str: String): Boolean {
    val patternStr = "[a-zA-Z][a-zA-Z_0-9]*|_[a-zA-Z_0-9]+"
    val pattern = Pattern.compile(patternStr)
    val matcher = pattern.matcher(str)
    return matcher.matches()
}

// for rust struct names
fun isValidRustPascalCase(str: String): Boolean {
    val patternStr = "[A-Z][a-zA-Z_0-9]*"
    val pattern = Pattern.compile(patternStr)
    val matcher = pattern.matcher(str)
    return matcher.matches()
}

// for rust module names
fun isValidRustSnakeCase(str: String): Boolean {
    val patternStr = "[a-z]+[a-z_0-9]*"
    val pattern = Pattern.compile(patternStr)
    val matcher = pattern.matcher(str)
    return matcher.matches()
}

fun pascalToSnakeCase(str:String): String {
    require(str.isNotEmpty())
    var result = str[0].toLowerCase().toString()
    for(c in str.slice(1..str.lastIndex)) {
        if (c.isUpperCase()) {
            result += "_"+c.toLowerCase()
        }else{
            result += c
        }
    }
    return result
}

/*
 * Returns true if given string appears in one of the lists of strict, reserved
 * or weak keywords of Rust as given here:
 * https://doc.rust-lang.org/beta/reference/keywords.html
 */
fun isRustKeyword(str: String): Boolean {
    val strictKeywords = arrayOf(
            "as",       "break",        "const",        "continue",     "crate",
            "else",     "enum",         "extern",       "false",        "fn",
            "for",      "if",           "impl",         "in",           "let",
            "loop",     "match",        "mod",          "move",         "mut",
            "pub",      "ref",          "return",       "self",         "Self",
            "static",   "struct",       "super",        "trait",        "true",
            "type",     "unsafe",       "use",          "where",        "while"
    )
    val reservedKeywords = arrayOf(
            "abstract",     "become",       "box",      "do",       "final",
            "macro",        "override",     "priv",     "typeof",   "unsized",
            "virtual",      "yield"
    )
    val weakKeywords = arrayOf(
            "union",        "'static",      "dyn"
    )
    return (    strictKeywords.contains(str) ||
            reservedKeywords.contains(str) ||
            weakKeywords.contains(str)
            )
}