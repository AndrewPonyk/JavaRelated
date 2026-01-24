// Sample file to test SU comments extension

// |su:1 This is the first SU comment
console.log("Hello world");

// |su:2 This is the second SU comment ++
const x = 5;

// |su:3 This is a complex issue --c
function complexFunction() {
  // |su:4 Another comment --n
  let result = 0;
  
  // |su:5 This is a hack --h
  result = result + 1;
  
  // |su:6 This is bad code --b
  eval("some dangerous code");
  
  // |su:7 This is untouched --u
  return result;
}

// |su:8 Another comment without status symbol
const y = 10;

// This is a regular comment, not an SU comment
const z = 15;

// |su:10 Final comment ++
console.log("End of file");