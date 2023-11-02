const radio1 = document.getElementById("one-way");
const radio2 = document.getElementById("roundtrip");
const radio3 = document.getElementById("multi-city");
const multicity = document.getElementById("multicity");
const returnDate = document.getElementById("returnDate");
radio1.addEventListener("click", () => {
	returnDate.disabled = true;
	multicity.selectIndex=0;
	multicity.disabled = true;
	returnDate.classList.add("disabled")
});
radio2.addEventListener("click", () => {
	returnDate.disabled = false;
	multicity.selectIndex=0;
	multicity.disabled = true;
	returnDate.classList.remove("disabled")
});
radio3.addEventListener("click", () => {
	multicity.selectIndex=0;
	multicity.disabled = false;
});

const options = document.getElementsByTagName("option");
const inputorigin = document.getElementById("origin");
const inputdest = document.getElementById("destination");

inputorigin.addEventListener("focusout", () => {
	flag = false;
	for (i = 0; i < options.length; i++) {
		if (options[i].innerHTML == inputorigin.value) {
			flag = true;
		}
	}
	if (!flag) {
		inputorigin.value = "";
	}
});

inputdest.addEventListener("focusout", () => {
	flag = false;
	for (i = 0; i < options.length; i++) {
		if (options[i].innerHTML == inputdest.value) {
			flag = true;
		}
	}
	if (!flag) {
		inputdest.value = "";
	}
});

var currentDt = new Date();
var dd = String(currentDt.getDate()).padStart(2, '0');
var mm = String(currentDt.getMonth() + 1).padStart(2, '0');
var yyyy = currentDt.getFullYear();

currentDt = yyyy + '-' + mm + '-' + dd;
/*('#set_bdate').attr('min', currentDt);
*/
