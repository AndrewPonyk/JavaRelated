%option noyywrap
%option c++

%{
// Reference Flex grammar for validating the handwritten lexer token set.
%}

IDENTIFIER  [a-zA-Z_][a-zA-Z0-9_]*
NUMBER      [0-9]+(\.[0-9]+)?
STRING      \"([^\"\\]|\\.)*\"
WS          [ \t\r\n]+

%%
"let"       return LET;
"true"      return TRUE;
"false"     return FALSE;
"null"      return NULL_LITERAL;
{IDENTIFIER} return IDENTIFIER;
{NUMBER}    return NUMBER;
{STRING}    return STRING;
"=="        return EQEQ;
"!="        return NEQ;
"<="        return LTE;
">="        return GTE;
"&&"        return AND;
"||"        return OR;
"|>"        return PIPE;
"="         return '=';
"!"         return '!';
"<"         return '<';
">"         return '>';
"+"         return '+';
"-"         return '-';
"*"         return '*';
"/"         return '/';
"%"         return '%';
"("         return '(';
")"         return ')';
"{"         return '{';
"}"         return '}';
"["         return '[';
"]"         return ']';
","         return ',';
"."         return '.';
":"         return ':';
";"         return ';';
{WS}        /* skip whitespace */;
.           return UNKNOWN;
%%
