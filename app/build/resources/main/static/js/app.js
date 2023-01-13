$(document).ready(
    function(){
        shortUrl();
        urlMetric();
        cpuMetric();
        uptimeMetric();
        setInterval(timeGraphic, 5000);
        setInterval(urlMetric(), 5000);
        setInterval(cpuMetric, 5000);
        setInterval(uptimeMetric, 1000);
    }
);

function changeState() {
    // Get the checkbox
    var checkBox = document.getElementById("qrcode");
    return checkBox.checked;
}

function shortUrl(){
    $("#shortener").submit(
        function (event) {
            event.preventDefault();
            $.ajax({
                type: "POST",
                url: "/api/link",
                data: $(this).serialize(),
                success: function (msg, status, request) {
                console.log("Llega a success")
                    urlMetric();
                    $("#result").html(
                        "<div class='alert alert-success lead'><a target='_blank' href='"
                        + request.getResponseHeader('Location')
                        + "'>"
                        + request.getResponseHeader('Location')
                        + "</a></div>"
                    );
                    if(changeState()){
                        $("#resultQR").html(
                            "<div class='alert alert-success lead'><a target='_blank' href='"
                            + request.getResponseHeader('Location')
                            + "/qrcode"
                            + "'>"
                            + request.getResponseHeader('Location')
                            + "/qrcode"
                            + "</a></div>"
                        );
                    }else{
                       $("#resultQR").html(
                           "<div></div>"
                       );
                    }
                },
                error: function (msg, status, request) {
                    jsonValue = jQuery.parseJSON( msg.responseText );
                    $("#result").html(
                        "<div class='alert alert-danger lead'>"
                        + jsonValue.message
                        +"</div>");
                }
            });
        }
    );
}
function infoMetrics(){
    $.ajax({
        type: "GET",
        url: "/api/metrics",
        data: $(this).serialize(),
        success: function (msg, status, request) {
            Object.keys(msg.list).forEach(function(key) {
                $("#"+key).html("<div class='alert alert-success lead'>"
                                       + msg.list[key] + "</div>\n");
            })
        },
        error: function () {
            $("#infoMetrics").html(
                "<div class='alert alert-danger lead'>ERROR</div>");
        }
    });
}

function urlMetric(){
    $.ajax({
        type: "GET",
        url: "/api/metrics/url",
        data: $(this).serialize(),
        success: function (msg, status, request) {
            $("#URLtotal").html(
                "<div class='alert alert-success lead'><a target='_blank' >"
                + msg.measurements[0].value + "</div>"
            );
        },
        error: function () {
            $("#URLtotal").html(
            "<div class='alert alert-danger lead'>ERROR</div>");
        }
    });
};

function cpuMetric(){
    $.ajax({
        type: "GET",
        url: "/api/metrics/process.cpu.usage",
        data: $(this).serialize(),
        success: function (msg, status, request) {
            $("#CPUusage").html(
                "<div class='alert alert-success lead'><a target='_blank' >"
                + msg.measurements[0].value.toFixed(8) + "</div>"
            );
        },
        error: function () {
            $("#CPUusage").html(
            "<div class='alert alert-danger lead'>ERROR</div>");
        }
    });
};

function uptimeMetric(){
    $.ajax({
        type: "GET",
        url: "/api/metrics/process.uptime",
        data: $(this).serialize(),
        success: function (msg, status, request) {
            $("#Uptime").html(
                "<div class='alert alert-success lead'><a target='_blank' >"
                + Math.floor(msg.measurements[0].value / 3600).toString().padStart(2, '0')
                + ":"
                + Math.floor(msg.measurements[0].value/60).toString().padStart(2, '0')
                + ":"
                + Math.floor(msg.measurements[0].value%60).toString().padStart(2, '0')
                + "</div>"
            );
        },
        error: function () {
            $("#Uptime").html(
            "<div class='alert alert-danger lead'>ERROR</div>");
        }
    });
};

function timeGraphic(){
    $.ajax({
        type: "GET",
        url: "/api/graphic",
        data: $(this).serialize(),
        success: function (msg, status, request) {
            console.log(msg)
            var newData = [70, 15, 30, 2, 50];
            myChart.data.datasets[0].data = msg.metric.newData;
            myChart.data.labels = msg.metric.newLabel;
            myChart.update();
        },
        error: function () {
            $("#graphic").html(
            "<div class='alert alert-danger lead'>ERROR</div>");
        }
    });
};