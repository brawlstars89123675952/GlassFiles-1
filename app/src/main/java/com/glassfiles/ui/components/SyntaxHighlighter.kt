package com.glassfiles.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * Lightweight regex-based syntax highlighter that returns an
 * [AnnotatedString] suitable for `Text(...)` rendering inside code fences.
 *
 * The intent here is "good enough for chat output", not a full-blown
 * tokenizer — no third-party highlighter dependency. Spans cover:
 *   - keywords (per language)
 *   - strings (single, double, backticks, triple)
 *   - numbers
 *   - line + block comments
 *   - annotations / attributes (`@Override`, `#[derive]`)
 *
 * Colour palette is supplied by the caller from `MaterialTheme.colorScheme`
 * tokens to honour CLAUDE.md's "no hardcoded hex" rule.
 */
data class CodeColors(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
)

/** Apply syntax highlighting for [lang] (case-insensitive). */
fun highlightCode(code: String, lang: String, colors: CodeColors): AnnotatedString {
    val rules = rulesFor(lang.lowercase())
    val builder = AnnotatedString.Builder(code)
    builder.addStyle(SpanStyle(color = colors.plain), 0, code.length)

    rules.forEach { rule ->
        rule.regex.findAll(code).forEach { m ->
            val color = when (rule.kind) {
                Kind.KEYWORD -> colors.keyword
                Kind.STRING -> colors.string
                Kind.NUMBER -> colors.number
                Kind.COMMENT -> colors.comment
                Kind.ANNOTATION -> colors.annotation
            }
            builder.addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
        }
    }
    return builder.toAnnotatedString()
}

private enum class Kind { KEYWORD, STRING, NUMBER, COMMENT, ANNOTATION }

private data class Rule(val regex: Regex, val kind: Kind)

private val NUMBER = Rule(Regex("\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*\\.?\\d*(?:[eE][+-]?\\d+)?[fFdDlL]?)\\b"), Kind.NUMBER)
private val DOUBLE_QUOTE = Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\""), Kind.STRING)
private val SINGLE_QUOTE = Rule(Regex("'(?:\\\\.|[^'\\\\])*'"), Kind.STRING)
private val BACKTICK = Rule(Regex("`(?:[^`\\\\]|\\\\.)*`"), Kind.STRING)
private val TRIPLE_DOUBLE = Rule(Regex("\"\"\"[\\s\\S]*?\"\"\"", RegexOption.MULTILINE), Kind.STRING)
private val LINE_COMMENT_DOUBLE_SLASH = Rule(Regex("//[^\\n]*"), Kind.COMMENT)
private val LINE_COMMENT_HASH = Rule(Regex("#[^\\n]*"), Kind.COMMENT)
private val LINE_COMMENT_DOUBLE_DASH = Rule(Regex("--[^\\n]*"), Kind.COMMENT)
private val BLOCK_COMMENT_C = Rule(Regex("/\\*[\\s\\S]*?\\*/", RegexOption.MULTILINE), Kind.COMMENT)
private val ANNOTATION = Rule(Regex("(?<![A-Za-z0-9_])@[A-Za-z_][A-Za-z0-9_]*"), Kind.ANNOTATION)
private val ATTRIBUTE_RUST = Rule(Regex("#!?\\[[^\\]\\n]+\\]"), Kind.ANNOTATION)

private fun keywordRule(words: List<String>): Rule = Rule(
    Regex("\\b(?:${words.joinToString("|")})\\b"),
    Kind.KEYWORD,
)

// Order matters: comments and strings must come BEFORE keywords/numbers so
// the comment/string spans override anything inside them.
private fun rulesFor(lang: String): List<Rule> = when (lang) {
    "kt", "kotlin" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, TRIPLE_DOUBLE, DOUBLE_QUOTE, SINGLE_QUOTE,
        ANNOTATION,
        keywordRule(KOTLIN_KEYWORDS),
        NUMBER,
    )
    "java" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, DOUBLE_QUOTE, SINGLE_QUOTE,
        ANNOTATION,
        keywordRule(JAVA_KEYWORDS),
        NUMBER,
    )
    "py", "python" -> listOf(
        TRIPLE_DOUBLE, DOUBLE_QUOTE, SINGLE_QUOTE,
        LINE_COMMENT_HASH,
        ANNOTATION,
        keywordRule(PYTHON_KEYWORDS),
        NUMBER,
    )
    "js", "javascript", "jsx", "ts", "typescript", "tsx" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, TRIPLE_DOUBLE, BACKTICK, DOUBLE_QUOTE, SINGLE_QUOTE,
        keywordRule(JS_KEYWORDS),
        NUMBER,
    )
    "rs", "rust" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, DOUBLE_QUOTE, SINGLE_QUOTE,
        ATTRIBUTE_RUST,
        keywordRule(RUST_KEYWORDS),
        NUMBER,
    )
    "go", "golang" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, BACKTICK, DOUBLE_QUOTE, SINGLE_QUOTE,
        keywordRule(GO_KEYWORDS),
        NUMBER,
    )
    "c", "cpp", "c++", "h", "hpp" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, DOUBLE_QUOTE, SINGLE_QUOTE,
        keywordRule(CPP_KEYWORDS),
        NUMBER,
    )
    "swift" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, DOUBLE_QUOTE, SINGLE_QUOTE,
        ANNOTATION,
        keywordRule(SWIFT_KEYWORDS),
        NUMBER,
    )
    "json" -> listOf(
        DOUBLE_QUOTE,
        keywordRule(listOf("true", "false", "null")),
        NUMBER,
    )
    "xml", "html", "svg", "androidxml" -> listOf(
        Rule(Regex("<!--[\\s\\S]*?-->", RegexOption.MULTILINE), Kind.COMMENT),
        DOUBLE_QUOTE, SINGLE_QUOTE,
        Rule(Regex("</?[A-Za-z][A-Za-z0-9_:.\\-]*"), Kind.KEYWORD),
        Rule(Regex("[A-Za-z_:][A-Za-z0-9_:.\\-]*(?==)"), Kind.ANNOTATION),
    )
    "sql" -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_DASH, DOUBLE_QUOTE, SINGLE_QUOTE,
        keywordRule(SQL_KEYWORDS),
        NUMBER,
    )
    "sh", "bash", "zsh", "shell" -> listOf(
        DOUBLE_QUOTE, SINGLE_QUOTE,
        LINE_COMMENT_HASH,
        keywordRule(SHELL_KEYWORDS),
        NUMBER,
    )
    "yml", "yaml" -> listOf(
        LINE_COMMENT_HASH,
        DOUBLE_QUOTE, SINGLE_QUOTE,
        keywordRule(listOf("true", "false", "null", "yes", "no", "on", "off")),
        Rule(Regex("^\\s*[A-Za-z_][\\w\\-]*(?=:)", RegexOption.MULTILINE), Kind.ANNOTATION),
        NUMBER,
    )
    "css", "scss" -> listOf(
        BLOCK_COMMENT_C, DOUBLE_QUOTE, SINGLE_QUOTE,
        Rule(Regex("[#.]?[A-Za-z_][\\w\\-]*(?=\\s*\\{)"), Kind.KEYWORD),
        Rule(Regex("[A-Za-z\\-]+(?=\\s*:)"), Kind.ANNOTATION),
        NUMBER,
    )
    else -> listOf(
        BLOCK_COMMENT_C, LINE_COMMENT_DOUBLE_SLASH, LINE_COMMENT_HASH,
        TRIPLE_DOUBLE, DOUBLE_QUOTE, SINGLE_QUOTE, BACKTICK,
        NUMBER,
    )
}

private val KOTLIN_KEYWORDS = listOf(
    "package", "import", "as", "is", "in", "out", "fun", "val", "var", "const", "lateinit",
    "class", "object", "interface", "data", "enum", "sealed", "abstract", "open", "final",
    "private", "public", "protected", "internal", "override", "operator", "infix", "inline",
    "noinline", "crossinline", "tailrec", "external", "actual", "expect", "by", "where",
    "if", "else", "when", "for", "while", "do", "return", "throw", "try", "catch", "finally",
    "break", "continue", "null", "true", "false", "this", "super", "init", "constructor",
    "typealias", "vararg", "reified", "suspend", "yield", "Unit", "Nothing", "String", "Int",
    "Long", "Boolean", "Float", "Double", "Char", "Byte", "Short", "Array", "List", "Map", "Set",
)

private val JAVA_KEYWORDS = listOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
    "volatile", "while", "true", "false", "null", "var", "record", "sealed", "permits", "yield",
)

private val PYTHON_KEYWORDS = listOf(
    "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
    "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
    "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
    "self", "cls", "match", "case",
)

private val JS_KEYWORDS = listOf(
    "abstract", "any", "as", "async", "await", "boolean", "break", "case", "catch", "class", "const",
    "constructor", "continue", "debugger", "declare", "default", "delete", "do", "else", "enum",
    "export", "extends", "false", "finally", "for", "from", "function", "get", "if", "implements",
    "import", "in", "instanceof", "interface", "is", "let", "module", "namespace", "new", "null",
    "number", "of", "package", "private", "protected", "public", "readonly", "require", "return",
    "set", "static", "string", "super", "switch", "symbol", "this", "throw", "true", "try", "type",
    "typeof", "undefined", "var", "void", "while", "with", "yield",
)

private val RUST_KEYWORDS = listOf(
    "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum", "extern",
    "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub",
    "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true", "type", "unsafe",
    "use", "where", "while", "i8", "i16", "i32", "i64", "i128", "u8", "u16", "u32", "u64", "u128",
    "f32", "f64", "bool", "char", "str", "String", "Vec", "Option", "Result", "Box",
)

private val GO_KEYWORDS = listOf(
    "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for",
    "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return", "select",
    "struct", "switch", "type", "var", "true", "false", "nil", "iota",
    "int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16", "uint32", "uint64",
    "uintptr", "float32", "float64", "complex64", "complex128", "bool", "byte", "rune", "string",
    "error",
)

private val CPP_KEYWORDS = listOf(
    "alignas", "alignof", "and", "asm", "auto", "bool", "break", "case", "catch", "char", "class",
    "const", "constexpr", "const_cast", "continue", "decltype", "default", "delete", "do", "double",
    "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false", "float", "for", "friend",
    "goto", "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not", "nullptr",
    "operator", "or", "private", "protected", "public", "register", "reinterpret_cast", "return",
    "short", "signed", "sizeof", "static", "static_cast", "struct", "switch", "template", "this",
    "throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual",
    "void", "volatile", "while",
)

private val SWIFT_KEYWORDS = listOf(
    "associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func", "import", "init",
    "inout", "internal", "let", "open", "operator", "private", "protocol", "public", "static", "struct",
    "subscript", "typealias", "var", "break", "case", "continue", "default", "defer", "do", "else",
    "fallthrough", "for", "guard", "if", "in", "repeat", "return", "switch", "where", "while", "as",
    "Any", "catch", "false", "is", "nil", "rethrows", "self", "Self", "super", "throw", "throws",
    "true", "try",
)

private val SQL_KEYWORDS = listOf(
    "select", "from", "where", "group", "by", "having", "order", "asc", "desc", "limit", "offset",
    "insert", "into", "values", "update", "set", "delete", "create", "table", "drop", "alter",
    "primary", "key", "foreign", "references", "constraint", "index", "unique", "not", "null",
    "default", "join", "inner", "outer", "left", "right", "full", "on", "as", "and", "or", "in",
    "between", "like", "is", "case", "when", "then", "else", "end", "union", "all", "distinct",
    "exists", "any", "with",
    "SELECT", "FROM", "WHERE", "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC", "LIMIT", "OFFSET",
    "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER",
    "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "INDEX", "UNIQUE", "NOT", "NULL",
    "DEFAULT", "JOIN", "INNER", "OUTER", "LEFT", "RIGHT", "FULL", "ON", "AS", "AND", "OR", "IN",
    "BETWEEN", "LIKE", "IS", "CASE", "WHEN", "THEN", "ELSE", "END", "UNION", "ALL", "DISTINCT",
    "EXISTS", "ANY", "WITH",
)

private val SHELL_KEYWORDS = listOf(
    "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac",
    "function", "return", "break", "continue", "exit", "export", "local", "readonly", "set", "unset",
    "true", "false", "echo", "printf", "read", "cd", "pwd", "ls", "cat", "grep", "sed", "awk", "find",
    "test", "source",
)
