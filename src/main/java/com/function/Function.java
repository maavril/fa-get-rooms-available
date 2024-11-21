package com.function;

import com.function.model.Reservations;
import com.function.model.ReservationsGet200Response;
import com.function.model.Rooms;
import com.function.model.RoomsGet200Response;
import com.google.gson.Gson;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

        // private static final String ROOT_URL = System.getenv("RESERVATION_API_URL");
        private static final String ROOT_URL = "https://salmon-sand-01006d203.5.azurestaticapps.net/data-api/api";
        private static final String RESERVATION_URL = ROOT_URL + "/reservations";
        private static final String ROOM_URL = ROOT_URL + "/rooms";

        private OkHttpClient client;

        public Function(){
                super();
                this.client = new OkHttpClient();
        }

        public Function(OkHttpClient client){
                super();
                this.client = client;
        }

        /**
         * This function listens at endpoint "/api/HttpExample". Two ways to invoke it
         * using "curl" command in bash:
         * 1. curl -d "HTTP Body" {your host}/api/HttpExample
         * 2. curl "{your
         * host}/api/reservationAvaillable?date=2024-12-22&hour=13&nbHours=2"
         */
        @FunctionName("reservationAvaillable")
        public HttpResponseMessage run(
                        @HttpTrigger(name = "req", methods = {
                                        HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                        final ExecutionContext context) {
                context.getLogger().info("Java HTTP trigger processed a request 2.");

                // Parse query parameter
                final Date date = Date.valueOf(request.getQueryParameters().get("date"));
                final Integer hour = Integer.valueOf(request.getQueryParameters().get("hour"));
                final Integer nbHours = Integer.valueOf(request.getQueryParameters().get("nbHours"));

                // ApiClient client = new ApiClient();
                // client.setBasePath(SAMPLE_POST_URL);
                // client.setDebugging(true);

                // ReservationsApi reservationsApi = new ReservationsApi(client);
                // ReservationsGet200Response response = null;
                // try {
                // response = reservationsApi.reservationsGet(null, null, null, null, null, 100,
                // null);
                // response.getValue().forEach(reservation -> {
                // System.out.println("Reservation ID: " + reservation.getReservationId());
                // });
                // } catch (ApiException e) {
                // System.err.println("HTTP Status: " + e.getCode());
                // System.err.println("Response Body: " + e.getResponseBody());
                // e.printStackTrace();
                // }

                List<Rooms> roomsList;
                List<Reservations> reservationList;
                try {
                        roomsList = getAllRooms();
                        reservationList = getReservationFromDate(date);
                } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        return request.createResponseBuilder(HttpStatus.SERVICE_UNAVAILABLE)
                                        .build();

                }

                List<BigDecimal> unavailableRoomIds = reservationList.stream()
                                .filter(res -> !(((hour + nbHours) <= res.getReservationHour().intValue())
                                                || (res.getReservationHour().intValue()
                                                                + res.getNbHours().intValue() <= hour)))
                                .map(res -> res.getRoomId()).distinct().collect(Collectors.toList());

                List<Rooms> response = roomsList.stream()
                                .filter(room -> !(unavailableRoomIds.contains(room.getRoomId())))
                                .collect(Collectors.toList());

                // Transformer l'objet en JSON
                Gson gson = new Gson();
                String jsonResponse = gson.toJson(response);

                return request.createResponseBuilder(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body(jsonResponse)
                                .build();
        }

        private List<Rooms> getAllRooms() throws IOException {
                Gson gson = new Gson();

                Request reqRooms = new Request.Builder()
                                .url(ROOM_URL)
                                .get()
                                .addHeader("Accept", "application/json")
                                .build();
                Response response = client.newCall(reqRooms).execute();
                if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        System.out.println("Response Body: " + jsonResponse);
                        RoomsGet200Response roomsResponse = gson.fromJson(
                                        jsonResponse,
                                        RoomsGet200Response.class);
                        return roomsResponse.getValue();
                } else {
                        System.err.println("Request failed. HTTP Status: " + response.code());
                        System.err.println("Response Body: " + response.body().string());
                        return new ArrayList<Rooms>();
                }
        }

        private List<Reservations> getReservationFromDate(Date date) throws IOException {
                Gson gson = new Gson();

                Request reqReservation = new Request.Builder()
                                .url(RESERVATION_URL)
                                .get()
                                .addHeader("Accept", "application/json")
                                .build();

                Response response = client.newCall(reqReservation).execute();
                if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        System.out.println("Response Body: " + jsonResponse);
                        ReservationsGet200Response reservationsResponse = gson.fromJson(
                                        jsonResponse,
                                        ReservationsGet200Response.class);
                        return reservationsResponse.getValue().stream()
                                        .filter(res -> date.equals(Date.valueOf(res.getReservationDate())))
                                        .collect(Collectors.toList());
                } else {
                        System.err.println("Request failed. HTTP Status: " + response.code());
                        System.err.println("Response Body: " + response.body().string());
                        return new ArrayList<Reservations>();
                }
        }
}
