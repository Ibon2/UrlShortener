$(document).ready(
    function(){
        shortUrl();
        infoMetrics();
        urlMetric();
        cpuMetric();
        uptimeMetric();
        setInterval(urlMetric, 5000);
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
                        + jsonValue.properties.error
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
                + msg.metric.measurement + "</div>"
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
        url: "/api/metrics/cpu",
        data: $(this).serialize(),
        success: function (msg, status, request) {
            $("#CPUusage").html(
                "<div class='alert alert-success lead'><a target='_blank' >"
                + msg.metric.measurement + "</div>"
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
        url: "/api/metrics/uptime",
        data: $(this).serialize(),
        success: function (msg, status, request) {
            $("#Uptime").html(
                "<div class='alert alert-success lead'><a target='_blank' >"
                + msg.metric.measurement + "</div>"
            );
        },
        error: function () {
            $("#Uptime").html(
            "<div class='alert alert-danger lead'>ERROR</div>");
        }
    });
};