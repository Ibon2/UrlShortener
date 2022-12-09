$(document).ready(
    function(){
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
        function urlMetric(){
            $.ajax({
                type: "GET",
                url: "/api/metrics/URL",
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
        setInterval(urlMetric, 5000);
        function cpuMetric(){
            $.ajax({
                type: "GET",
                url: "/api/metrics/CPU",
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
        setInterval(cpuMetric, 5000);
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
        setInterval(uptimeMetric, 1000);
        $("#shortener").submit(
            function (event) {
                event.preventDefault();
                $.ajax({
                    type: "POST",
                    url: "/api/link",
                    data: $(this).serialize(),
                    success: function (msg, status, request) {
                        $("#result").html(
                            "<div class='alert alert-success lead'><a target='_blank' href='"
                            + request.getResponseHeader('Location')
                            + "'>"
                            + request.getResponseHeader('Location')
                            + "</a></div>"
                    );
                    },
                    error: function () {
                        $("#result").html(
                            "<div class='alert alert-danger lead'>ERROR</div>");
                    }
                });
            }
        );
    }
);

