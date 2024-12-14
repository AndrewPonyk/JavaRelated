; Correct asm 16 bit dos program to calculate factorial of 5
.model small
.stack 100h

.data
    msg db "Factorial of 5 is: $", 0Dh, 0Ah, "$"  ; Message to display
    result db 6 dup(' ')  ; Buffer to store the result as a string (max 5 digits + null terminator)

.code
main proc
    mov ax, @data
    mov ds, ax

    ; Calculate factorial of 5
    mov cx, 5
    mov ax, 1

factorial_loop:
    mul cx
    loop factorial_loop

    ; Convert the result to a string
    lea di, result + 5  ; Point to the end of the buffer (6th byte for null terminator)
    mov bx, 10          ; Divisor for decimal conversion
    mov byte ptr [di], '$' ; Null-terminate the string
    dec di              ; Move pointer backward

convert_loop:
    xor dx, dx          ; Clear DX before division
    div bx              ; Divide AX by 10, quotient in AX, remainder in DX
    add dl, '0'         ; Convert remainder to ASCII digit
    mov [di], dl        ; Store ASCII digit in buffer
    dec di              ; Move pointer backward
    test ax, ax         ; Check if quotient is zero
    jnz convert_loop    ; Repeat until quotient is zero

    ; Display the message and result
    lea dx, msg
    mov ah, 09h
    int 21h

    lea dx, result
    mov ah, 09h
    int 21h

    ; Exit the program
    mov ah, 4Ch
    int 21h

main endp
end main