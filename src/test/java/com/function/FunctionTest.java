package com.function;

import com.microsoft.azure.functions.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for Function class.
 */
public class FunctionTest {
    /**
     * Unit test for HttpTriggerJava method.
     */
    @Test
    public void testHttpTriggerJava() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        final Map<String, String> queryParams = new HashMap<>();

        queryParams.put("date", "2024-12-22");
        queryParams.put("hour", "12");
        queryParams.put("nbHours", "2");
        doReturn(queryParams).when(req).getQueryParameters();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        // Mock OkHttpClient and HTTP responses
        OkHttpClient mockedClient = mock(OkHttpClient.class);
        mockHttpCalls(mockedClient);

        // Inject mocked OkHttpClient into the Function
        Function function = new Function(mockedClient);

        // Call the function
        final HttpResponseMessage response = function.run(req, context);
        // Verify
        assertEquals(HttpStatus.OK, response.getStatus());

        // Verify the body of the response
        assertNotNull(response.getBody());
        String jsonBody = (String) response.getBody();

        // Parse the JSON response
        Gson gson = new Gson();
        List<Map<String, Object>> rooms = gson.fromJson(jsonBody, new TypeToken<List<Map<String, Object>>>() {
        }.getType());

        // Print the response for debugging
        System.out.println("Response: " + jsonBody);

        // Assert the size of the response list
        assertEquals(1, rooms.size());

        // Assert details of the second room
        Map<String, Object> room = rooms.get(0);
        assertEquals(2.0, room.get("room_id")); // Note: Gson maps numbers to Double by default
        assertEquals("Meeting Room A", room.get("room_name"));

    }

    private void mockHttpCalls(OkHttpClient mockedClient) throws IOException {
        // Mock response for getAllRooms
        Response mockedRoomsResponse = createMockedResponse(
                200,
                new Gson().toJson(
                        Map.of("value", List.of(
                                Map.of("room_id", 1, "room_name", "Conference Room"),
                                Map.of("room_id", 2, "room_name", "Meeting Room A")))));

        // Mock response for getReservationFromDate
        Response mockedReservationsResponse = createMockedResponse(
                200,
                new Gson().toJson(
                        Map.of("value", List.of(
                                Map.of("reservation_id", 1, "reservation_date", "2024-12-22", "reservation_hour", 13,
                                        "nb_hours", 2, "room_id", 1, "client_mail", "mathieu.avril@spikeelabs.fr")))));

        // Stub calls in OkHttpClient
        Call mockedCall = mock(Call.class);

        when(mockedClient.newCall(any(Request.class)))
                .thenAnswer(invocation -> {
                    Request request = invocation.getArgument(0);
                    if (request.url().toString().contains("rooms")) {
                        return createMockedCall(mockedRoomsResponse);
                    } else if (request.url().toString().contains("reservations")) {
                        return createMockedCall(mockedReservationsResponse);
                    }
                    throw new IllegalArgumentException("Unexpected URL: " + request.url());
                });
    }

    private Call createMockedCall(Response mockedResponse) throws IOException {
        Call mockedCall = mock(Call.class);
        when(mockedCall.execute()).thenReturn(mockedResponse);
        return mockedCall;
    }

    private Response createMockedResponse(int statusCode, String body) {
        return new Response.Builder()
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("Mocked response")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .request(new Request.Builder().url("http://mocked.url").build())
                .build();
    }
}
