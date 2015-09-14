$.ajax({
  dataType: "json",
  url: "http://54.188.181.255:8080/hellojsfcodegeeks/1.json",
  success: function(data){
	  alert(data);
  }
});