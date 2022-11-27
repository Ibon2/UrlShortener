$(document).ready(
    function(){
         $("#shortener").submit(
             function (event) {
                 event.preventDefault();
                 $.ajax({
                     type: "POST",
                     url: "/api/link",
                     data: $(this).serialize(),
                     success: function (msg, status, request) {
                        let metrics = "";
                        Object.keys(msg.list).forEach(function(key) {
                          metrics += "<div class='alert alert-success lead'>"
                                  + msg.list[key]
                                  + "</div>\n";
                        })
                         $("#result").html(
                             "<div class='alert alert-success lead'><a target='_blank' href='"
                                + request.getResponseHeader('Location')
                                + "'>"
                                + request.getResponseHeader('Location')
                                + "</a></div>"
                                + metrics
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

