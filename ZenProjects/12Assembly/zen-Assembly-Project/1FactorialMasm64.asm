.data
    msg db "Enter a single digit number: $"
    inputBuffer db 2 ; 1 byte for the digit, 1 for null terminator
    error_msg db "Invalid input. Please enter a single digit number."
    factorial_msg db "Factorial of ", 0
    result_msg db " is: ", 0

.data?
    hConsoleInput dq ?
    hConsoleOutput dq ?
    bytesWritten dq ?

.code
main proc
    ; Get console handles
    mov ecx, -10  ; STD_INPUT_HANDLE
    call GetStdHandle
    mov hConsoleInput, rax

    mov ecx, -11  ; STD_OUTPUT_HANDLE
    call GetStdHandle
    mov hConsoleOutput, rax

    ; Calculate factorial of 5
    mov rcx, 5
    mov rax, 1

factorial_loop:
    imul rax, rcx
    loop factorial_loop

    ; Display the prompt
    lea rdx, msg
    mov r8, SIZEOF msg - 1
    mov rcx, hConsoleOutput
    call WriteConsoleA

    ; Read a single digit number from the console
    lea rdx, inputBuffer
    mov r8, 1
    mov rcx, hConsoleInput
    call ReadConsoleA

    ; Check if the input is a valid digit
    cmp byte ptr [inputBuffer], '0'
    jb invalid_input
    cmp byte ptr [inputBuffer], '9'
    ja invalid_input

    ; Convert the ASCII digit to a numbepr
    sub byte ptr [inputBuffer], '0'
    movzx eax, byte ptr [inputBuffer]

    ; Add the input number to the factorial result
    add rax, factorialResult

    ; Convert the result to a string
    lea rdi, result + 9
    mov byte ptr [rdi], 0
    dec rdi

convert_loop:
    xor rdx, rdx
    mov rbx, 10
    div rbx
    add dl, '0'
    mov [rdi], dl
    dec rdi
    test rax, rax
    jnz convert_loop

    ; Display the factorial result
    lea rdx, factorial_msg
    mov r8, SIZEOF factorial_msg - 1
    mov rcx, hConsoleOutput
    call WriteConsoleA

    lea rdx, inputBuffer
    mov r8, 1
    mov rcx, hConsoleOutput
    call WriteConsoleA

    lea rdx, result_msg
    mov r8, SIZEOF result_msg - 1
    mov rcx, hConsoleOutput
    call WriteConsoleA

    lea rdx, result
    mov r8, 10
    mov rcx, hConsoleOutput
    call WriteConsoleA

    jmp exit_program

invalid_input:
    lea rdx, error_msg
    mov r8, SIZEOF error_msg - 1
    mov rcx, hConsoleOutput
    call WriteConsoleA

exit_program:
    xor ecx, ecx
    call ExitProcess
main endp

end