

# Translations with Access Control Focus

## 1. Python
```python
class Animal:
    def __init__(self, name, age): self._name, self._age = name, age  # Protected by convention (_)
    def get_name(self): return self._name
    def get_age(self): return self._age
    def speak(self): print(f"{self.get_name()} makes a sound")

class Dog(Animal):
    def __init__(self, name, age, trained): 
        super().__init__(name, age)
        self.__trained = trained  # Private with double underscore
    def is_trained(self): return self.__trained
    def speak(self): print(f"{self.get_name()} barks! Age: {self.get_age()}, Trained: {self.__trained}")

def main():
    count, price, grade, active, text, nums = 0, 19.99, 'A', True, "Hello", [1, 2, 3]
    animals = []
    n = int(input("How many dogs? "))
    for i in range(n): animals.append(Dog(input("Name: "), i + 1, i % 2 == 0))
    for a in animals:
        if isinstance(a, Dog) and a.is_trained(): print("[TRAINED] ", end="")
        a.speak()
    i = 0
    while i < len(nums): print(f"Num: {nums[i]}"); i += 1
    print(f"Stats: count={count}, price={price:.2f}, grade={grade}, active={active}, text={text}")

if __name__ == "__main__": main()
```

## 2. C++
```cpp
#include <iostream>
#include <vector>
#include <string>
#include <memory>
using namespace std;

class Animal {
protected:  // Protected fields
    string name;
    int age;
public:
    Animal(string n, int a) : name(n), age(a) {}
    string getName() const { return name; }
    int getAge() const { return age; }
    virtual void speak() { cout << getName() << " makes a sound" << endl; }
    virtual bool isTrained() const { return false; }
    virtual ~Animal() {}  // Virtual destructor
};

class Dog : public Animal {
private:  // Private field
    bool trained;
public:
    Dog(string n, int a, bool t) : Animal(n, a), trained(t) {}
    bool isTrained() const override { return trained; }
    void speak() override { cout << getName() << " barks! Age: " << getAge() << ", Trained: " << boolalpha << trained << endl; }
};

int main() {
    int count = 0; double price = 19.99; char grade = 'A'; bool active = true; string text = "Hello"; int nums[] = {1, 2, 3};
    vector<unique_ptr<Animal>> animals;
    cout << "How many dogs? "; int n; cin >> n; cin.ignore();
    for (int i = 0; i < n; i++) {
        cout << "Name: "; string name; getline(cin, name);
        animals.push_back(make_unique<Dog>(name, i + 1, i % 2 == 0));
    }
    for (auto& a : animals) {
        if (a->isTrained()) cout << "[TRAINED] ";
        a->speak();
    }
    int i = 0; while (i < 3) cout << "Num: " << nums[i++] << endl;
    cout << "Stats: count=" << count << ", price=" << fixed << setprecision(2) << price << ", grade=" 
         << grade << ", active=" << boolalpha << active << ", text=" << text << endl;
}
```

## 3. JavaScript
```javascript
class Animal {
    #name; #age;  // Private fields (ES2020+)
    constructor(name, age) { this.#name = name; this.#age = age; }
    getName() { return this.#name; }
    getAge() { return this.#age; }
    speak() { console.log(`${this.getName()} makes a sound`); }
}

class Dog extends Animal {
    #trained;  // Private field
    constructor(name, age, trained) { super(name, age); this.#trained = trained; }
    isTrained() { return this.#trained; }
    speak() { console.log(`${this.getName()} barks! Age: ${this.getAge()}, Trained: ${this.#trained}`); }
}

async function main() {
    const count = 0, price = 19.99, grade = 'A', active = true, text = "Hello", nums = [1, 2, 3], animals = [];
    const readline = require('readline').createInterface({input: process.stdin, output: process.stdout});
    const question = (q) => new Promise(resolve => readline.question(q, resolve));
    
    const n = parseInt(await question("How many dogs? "));
    for (let i = 0; i < n; i++) animals.push(new Dog(await question("Name: "), i + 1, i % 2 === 0));
    
    for (const a of animals) {
        if (a.isTrained()) process.stdout.write("[TRAINED] ");
        a.speak();
    }
    
    let i = 0; while (i < nums.length) console.log(`Num: ${nums[i++]}`);
    console.log(`Stats: count=${count}, price=${price.toFixed(2)}, grade=${grade}, active=${active}, text=${text}`);
    readline.close();
}
main();
```

## 4. TypeScript
```typescript
class Animal {
    protected name: string;
    protected age: number;
    
    constructor(name: string, age: number) { this.name = name; this.age = age; }
    getName(): string { return this.name; }
    getAge(): number { return this.age; }
    speak(): void { console.log(`${this.getName()} makes a sound`); }
}

class Dog extends Animal {
    private trained: boolean;
    
    constructor(name: string, age: number, trained: boolean) { super(name, age); this.trained = trained; }
    isTrained(): boolean { return this.trained; }
    speak(): void { console.log(`${this.getName()} barks! Age: ${this.getAge()}, Trained: ${this.trained}`); }
}

import * as readline from 'readline';
async function main(): Promise<void> {
    const count = 0, price = 19.99, grade = 'A', active = true, text = "Hello", nums = [1, 2, 3], animals: Animal[] = [];
    const rl = readline.createInterface({input: process.stdin, output: process.stdout});
    const question = (q: string): Promise<string> => new Promise(resolve => rl.question(q, resolve));
    
    const n: number = parseInt(await question("How many dogs? "));
    for (let i = 0; i < n; i++) animals.push(new Dog(await question("Name: "), i + 1, i % 2 === 0));
    
    for (const a of animals) {
        if (a instanceof Dog && a.isTrained()) process.stdout.write("[TRAINED] ");
        a.speak();
    }
    
    let i = 0; while (i < nums.length) console.log(`Num: ${nums[i++]}`);
    console.log(`Stats: count=${count}, price=${price.toFixed(2)}, grade=${grade}, active=${active}, text=${text}`);
    rl.close();
}
main();
```

## 5. PHP
```php
<?php
class Animal {
    protected string $name;  // Protected properties
    protected int $age;
    
    public function __construct(string $name, int $age) { $this->name = $name; $this->age = $age; }
    public function getName(): string { return $this->name; }
    public function getAge(): int { return $this->age; }
    public function speak(): void { echo "{$this->getName()} makes a sound\n"; }
}

class Dog extends Animal {
    private bool $trained;  // Private property
    
    public function __construct(string $name, int $age, bool $trained) { 
        parent::__construct($name, $age); $this->trained = $trained; 
    }
    public function isTrained(): bool { return $this->trained; }
    public function speak(): void { echo "{$this->getName()} barks! Age: {$this->getAge()}, Trained: " . 
                                        ($this->trained ? "true" : "false") . "\n"; }
}

$count = 0; $price = 19.99; $grade = 'A'; $active = true; $text = "Hello"; $nums = [1, 2, 3]; $animals = [];
echo "How many dogs? "; $n = (int)trim(fgets(STDIN));
for ($i = 0; $i < $n; $i++) { echo "Name: "; $name = trim(fgets(STDIN)); $animals[] = new Dog($name, $i + 1, $i % 2 == 0); }

foreach ($animals as $a) {
    if ($a instanceof Dog && $a->isTrained()) echo "[TRAINED] ";
    $a->speak();
}

$i = 0; while ($i < count($nums)) { echo "Num: {$nums[$i]}\n"; $i++; }
printf("Stats: count=%d, price=%.2f, grade=%s, active=%s, text=%s\n", 
       $count, $price, $grade, $active ? "true" : "false", $text);
```

## 6. C#
```csharp
using System;
using System.Collections.Generic;

class Animal {
    protected string name;  // Protected fields
    protected int age;
    
    public Animal(string name, int age) { this.name = name; this.age = age; }
    public string GetName() => name;
    public int GetAge() => age;
    public virtual void Speak() => Console.WriteLine($"{GetName()} makes a sound");
}

class Dog : Animal {
    private bool trained;  // Private field
    
    public Dog(string name, int age, bool trained) : base(name, age) { this.trained = trained; }
    public bool IsTrained() => trained;
    public override void Speak() => Console.WriteLine($"{GetName()} barks! Age: {GetAge()}, Trained: {trained}");
}

class Program {
    static void Main() {
        int count = 0; double price = 19.99; char grade = 'A'; bool active = true; string text = "Hello"; 
        int[] nums = { 1, 2, 3 }; var animals = new List<Animal>();
        
        Console.Write("How many dogs? "); int n = int.Parse(Console.ReadLine());
        for (int i = 0; i < n; i++) {
            Console.Write("Name: "); string name = Console.ReadLine();
            animals.Add(new Dog(name, i + 1, i % 2 == 0));
        }
        
        foreach (Animal a in animals) {
            if (a is Dog d && d.IsTrained()) Console.Write("[TRAINED] ");
            a.Speak();
        }
        
        int i = 0; while (i < nums.Length) Console.WriteLine($"Num: {nums[i++]}");
        Console.WriteLine($"Stats: count={count}, price={price:F2}, grade={grade}, active={active}, text={text}");
    }
}
```

## 7. Go
```go
package main

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
)

type Animal struct {
	name string // Go doesn't have protected, but unexported fields are similar
	age  int
}

func NewAnimal(name string, age int) Animal { return Animal{name: name, age: age} }
func (a *Animal) GetName() string { return a.name }
func (a *Animal) GetAge() int { return a.age }
func (a *Animal) Speak() { fmt.Printf("%s makes a sound\n", a.GetName()) }

type Dog struct {
	Animal
	trained bool // Private by being unexported
}

func NewDog(name string, age int, trained bool) Dog { return Dog{Animal: NewAnimal(name, age), trained: trained} }
func (d *Dog) IsTrained() bool { return d.trained }
func (d *Dog) Speak() { fmt.Printf("%s barks! Age: %d, Trained: %t\n", d.GetName(), d.GetAge(), d.trained) }

func main() {
	count, price, grade, active, text := 0, 19.99, 'A', true, "Hello"
	nums := []int{1, 2, 3}
	var dogs []Dog
	
	scanner := bufio.NewScanner(os.Stdin)
	fmt.Print("How many dogs? ")
	scanner.Scan()
	n, _ := strconv.Atoi(strings.TrimSpace(scanner.Text()))
	
	for i := 0; i < n; i++ {
		fmt.Print("Name: ")
		scanner.Scan()
		name := scanner.Text()
		dogs = append(dogs, NewDog(name, i+1, i%2 == 0))
	}
	
	for i := range dogs {
		if dogs[i].IsTrained() { fmt.Print("[TRAINED] ") }
		dogs[i].Speak()
	}
	
	i := 0; for i < len(nums) { fmt.Printf("Num: %d\n", nums[i]); i++ }
	fmt.Printf("Stats: count=%d, price=%.2f, grade=%c, active=%t, text=%s\n", 
               count, price, grade, active, text)
}
```

## 8. Rust
```rust
use std::io::{self, Write};

trait Animal {
    fn get_name(&self) -> &str;
    fn get_age(&self) -> i32;
    fn speak(&self);
    fn is_trained(&self) -> bool { false }
}

struct AnimalBase {  // Fields private by default in Rust
    name: String,
    age: i32,
}

impl Animal for AnimalBase {
    fn get_name(&self) -> &str { &self.name }
    fn get_age(&self) -> i32 { self.age }
    fn speak(&self) { println!("{} makes a sound", self.get_name()); }
}

struct Dog {
    base: AnimalBase,
    trained: bool,  // Private by default
}

impl Animal for Dog {
    fn get_name(&self) -> &str { self.base.get_name() }
    fn get_age(&self) -> i32 { self.base.get_age() }
    fn speak(&self) { println!("{} barks! Age: {}, Trained: {}", self.get_name(), self.get_age(), self.trained); }
    fn is_trained(&self) -> bool { self.trained }
}

fn main() {
    let count = 0; let price = 19.99; let grade = 'A'; let active = true; let text = "Hello"; 
    let nums = [1, 2, 3]; let mut animals: Vec<Box<dyn Animal>> = Vec::new();
    
    print!("How many dogs? "); io::stdout().flush().unwrap();
    let mut input = String::new();
    io::stdin().read_line(&mut input).unwrap();
    let n: i32 = input.trim().parse().unwrap();
    
    for i in 0..n {
        print!("Name: "); io::stdout().flush().unwrap();
        let mut name = String::new();
        io::stdin().read_line(&mut name).unwrap();
        let dog = Box::new(Dog { base: AnimalBase { name: name.trim().to_string(), age: i + 1 }, 
                              trained: i % 2 == 0 });
        animals.push(dog);
    }
    
    for a in &animals {
        if a.is_trained() { print!("[TRAINED] "); }
        a.speak();
    }
    
    let mut i = 0; while i < nums.len() { println!("Num: {}", nums[i]); i += 1; }
    println!("Stats: count={}, price={:.2}, grade={}, active={}, text={}",
             count, price, grade, active, text);
}
```

## 9. Kotlin
```kotlin
open class Animal(protected val name: String, protected val age: Int) {  // Protected properties
    open fun getName(): String = name
    open fun getAge(): Int = age
    open fun speak() = println("${getName()} makes a sound")
    open fun isTrained(): Boolean = false
}

class Dog(name: String, age: Int, private val trained: Boolean) : Animal(name, age) {  // Private property
    override fun isTrained(): Boolean = trained
    override fun speak() = println("${getName()} barks! Age: ${getAge()}, Trained: $trained")
}

fun main() {
    val count = 0; val price = 19.99; val grade = 'A'; val active = true; val text = "Hello"
    val nums = intArrayOf(1, 2, 3); val animals = mutableListOf<Animal>()
    
    val scanner = java.util.Scanner(System.`in`)
    print("How many dogs? ")
    val n = scanner.nextInt()
    scanner.nextLine()
    
    for (i in 0 until n) {
        print("Name: ")
        val name = scanner.nextLine()
        animals.add(Dog(name, i + 1, i % 2 == 0))
    }
    
    for (a in animals) {
        if (a.isTrained()) print("[TRAINED] ")
        a.speak()
    }
    
    var i = 0; while (i < nums.size) println("Num: ${nums[i++]}")
    println("Stats: count=$count, price=%.2f, grade=$grade, active=$active, text=$text".format(price))
    scanner.close()
}
```

## 10. Elixir
```elixir
defmodule Animal do
  defstruct [:name, :age]  # No direct access control, use module patterns instead
  
  def new(name, age), do: %Animal{name: name, age: age}
  def get_name(%Animal{name: name}), do: name
  def get_age(%Animal{age: age}), do: age
  def speak(animal), do: IO.puts("#{get_name(animal)} makes a sound")
  def is_trained?(_), do: false
end

defmodule Dog do
  defstruct [:animal, :trained]  # Composition rather than inheritance
  
  def new(name, age, trained), do: %Dog{animal: Animal.new(name, age), trained: trained}
  def get_name(%Dog{animal: animal}), do: Animal.get_name(animal)
  def get_age(%Dog{animal: animal}), do: Animal.get_age(animal)
  def is_trained?(%Dog{trained: trained}), do: trained
  def speak(dog), do: IO.puts("#{get_name(dog)} barks! Age: #{get_age(dog)}, Trained: #{dog.trained}")
end

defmodule Demo do
  def main do
    count = 0; price = 19.99; grade = ?A; active = true; text = "Hello"; nums = [1, 2, 3]
    
    IO.write("How many dogs? ")
    {n, _} = Integer.parse(IO.gets(""))
    
    animals = Enum.map(0..(n-1), fn i ->
      IO.write("Name: ")
      name = String.trim(IO.gets(""))
      Dog.new(name, i + 1, rem(i, 2) == 0)
    end)
    
    Enum.each(animals, fn animal ->
      if Dog.is_trained?(animal), do: IO.write("[TRAINED] ")
      Dog.speak(animal)
    end)
    
    print_nums(nums, 0)
    
    IO.puts("Stats: count=#{count}, price=#{:io_lib.format("~.2f", [price])}, " <>
            "grade=#{List.to_string([grade])}, active=#{active}, text=#{text}")
  end
  
  defp print_nums(nums, i) when i < length(nums) do
    IO.puts("Num: #{Enum.at(nums, i)}")
    print_nums(nums, i + 1)
  end
  defp print_nums(_, _), do: nil
end

Demo.main()
```