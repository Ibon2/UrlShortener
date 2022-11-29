$(document).ready(
    function(){
      $.ajax({
          type: "GET",
          url: "/api/metrics/URL",
          data: $(this).serialize(),
          success: function (msg, status, request) {
              $("#URLtotal").html(
                  "<div class='alert alert-success lead'><a target='_blank' >"
                     + msg.total.urlShortenedTotal + " URL(s) recortadas"
                     );
          },
          error: function () {
              $("#result").html(
                  "<div class='alert alert-danger lead'>ERROR</div>");
          }
      });
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

