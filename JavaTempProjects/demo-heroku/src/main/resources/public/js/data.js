var oracleUrl = "historicdata/";
function toggleColor(element, classAttribute) {
  $(element).toggleClass(classAttribute);
}

function trimAndStripString(str){
    var result = str.trim();
    result = result.replaceAll("%20", " ");
    result = trimChar(result, ')');
    result = trimChar(result, '(');

    if(result.indexOf("(") > 0){
     result = result.substring(result.indexOf("(")+1);
    }
    return result;
}


function trimChar(string, charToRemove) {
    while(string.charAt(0)==charToRemove) {
        string = string.substring(1);
    }

    while(string.charAt(string.length-1)==charToRemove) {
        string = string.substring(0,string.length-1);
    }

    return string;
}

$(document).ready(function () {
    console.log("history data page");
    var queryString = window.location.search;

    filterAndDrawBets(queryString);


    $("#filter").click(function () {
        var player1Title = $("#player1Title").val();
        var player2Title = $("#player2Title").val();
        var sport = $("#sportSelect").val();
        console.log(player1Title + "|" + player2Title + "|" + sport + "|")

        var queryString = "player1Title=" + player1Title + "&player2Title=" + player2Title + "&sport=" + sport;
        console.log(queryString);
        filterAndDrawBets(queryString);
    });

    function filterAndDrawBets(params) {
        var rootElem = $("#historic");
        rootElem.empty();
        if(!params.startsWith("?")){
            params = "?"+params;
        }
        params = params.replace("%20", " ");

        $.get(oracleUrl  + params, function (data) {
            var historicData = data.data;

            for (const titleDateTimeKey in historicData) {
                var itemString = "<div class='betInfo'><b>" + titleDateTimeKey + "</b>";

                var boldTitle = "";
                if (titleDateTimeKey.indexOf(",") >= 0) {

                    var titlePart = titleDateTimeKey.split(",");
                    if (titlePart[0].indexOf("-") >= 0) {
                        boldTitle = "<span ondblclick='toggleColor(this, \"red\")' onclick='toggleColor(this, \"green\")'>"
                            + "<a href='?player1Title=" + trimAndStripString(titlePart[0].split("-")[0]) + "'>link</a>"
                            + titlePart[0].split("-")[0] + "</span>" +
                            "-<span ondblclick='toggleColor(this, \"red\")' onclick='toggleColor(this, \"green\")'>"
                            + "<a href='?player2Title=" + trimAndStripString(titlePart[0].split("-")[1]) + "'>link</a>"
                            + titlePart[0].split("-")[1] + "</span>";
                        boldTitle+= titleDateTimeKey.split(",")[1];
                    }
                }

                if(boldTitle.length >0){
                    itemString = "<div class='betInfo'><b>" + boldTitle + "</b>";
                }

                for (var syncItem in historicData[titleDateTimeKey]) {
                    if(itemString.indexOf("|")<0){
                        //retrieve initial odds:

                        itemString+= "<span class='bolder'>" + historicData[titleDateTimeKey][syncItem].coef1 + "," +
                            historicData[titleDateTimeKey][syncItem].coef2 + "</span>";
                    }
                    //here add scores
                    itemString += "<span>" + historicData[titleDateTimeKey][syncItem].score + "| </span>";
                }
                itemString += "</div>";
                rootElem.append(itemString);
            }
        });
    }
})
