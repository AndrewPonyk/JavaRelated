%{
// Reference Bison grammar for validating syntax accepted by the production
// handwritten recursive descent parser with Pratt expressions.
%}

%token LET TRUE FALSE NULL_LITERAL IDENTIFIER NUMBER STRING UNKNOWN
%token EQEQ NEQ LTE GTE AND OR PIPE

%%
module
    : statements
    ;

statements
    : /* empty */
    | statements statement
    ;

statement
    : LET IDENTIFIER '=' expression ';'
    | expression ';'
    ;

expression
    : primary
    | '!' expression
    | '-' expression
    | expression '+' expression
    | expression '-' expression
    | expression '*' expression
    | expression '/' expression
    | expression '%' expression
    | expression EQEQ expression
    | expression NEQ expression
    | expression '<' expression
    | expression LTE expression
    | expression '>' expression
    | expression GTE expression
    | expression AND expression
    | expression OR expression
    | expression PIPE expression
    | expression '(' arguments ')'
    | expression '[' expression ']'
    | expression '.' IDENTIFIER
    ;

primary
    : IDENTIFIER
    | NUMBER
    | STRING
    | TRUE
    | FALSE
    | NULL_LITERAL
    | '[' arguments ']'
    | '{' fields '}'
    | '(' expression ')'
    ;

arguments
    : /* empty */
    | expression
    | arguments ',' expression
    ;

fields
    : /* empty */
    | IDENTIFIER ':' expression
    | fields ',' IDENTIFIER ':' expression
    ;
%%
