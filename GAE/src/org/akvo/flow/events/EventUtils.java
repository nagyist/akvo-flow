/*
 *  Copyright (C) 2015 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo FLOW.
 *
 *  Akvo FLOW is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo FLOW is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.flow.events;

import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jackson.map.ObjectMapper;
import org.waterforpeople.mapping.app.web.rest.security.user.GaeUser;

import com.google.appengine.api.backends.BackendServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;

public class EventUtils {

    private static Logger log = Logger.getLogger(EventUtils.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public enum EventSourceType {
        USER, DEVICE, SENSOR, WEBFORM, API, UNKNOWN, SYSTEM
    };

    public enum EntityType {
        SURVEY_GROUP, FORM, QUESTION_GROUP, QUESTION, DATA_POINT, FORM_INSTANCE, ANSWER, DEVICE_FILE
    };

    // names of kinds in Google App Engine
    static public class Kind {
        public static final String SURVEY_GROUP = "SurveyGroup";
        public static final String FORM = "Survey";
        public static final String QUESTION_GROUP = "QuestionGroup";
        public static final String QUESTION = "Question";
        public static final String DATA_POINT = "SurveyedLocale";
        public static final String FORM_INSTANCE = "SurveyInstance";
        public static final String ANSWER = "QuestionAnswerStore";
        public static final String DEVICE_FILE = "DeviceFiles";
    }

    // How we name the actions
    static class Action {
        public static final String SURVEY_GROUP = "surveyGroup";
        public static final String FORM = "form";
        public static final String QUESTION_GROUP = "questionGroup";
        public static final String QUESTION = "question";
        public static final String DATA_POINT = "dataPoint";
        public static final String FORM_INSTANCE = "formInstance";
        public static final String ANSWER = "answer";
        public static final String DEVICE_FILE = "deviceFile";

        public static final String DELETED = "Deleted";
        public static final String CREATED = "Created";
        public static final String UPDATED = "Updated";

        public static final String UNIFIED_LOG_NOTIFIED = "unifiedLogNotified";

    }

    static class Key {
        public static final String ID = "id";
        public static final String EMAIL = "email";
        public static final String TIMESTAMP = "timestamp";
        public static final String SOURCE = "source";
        public static final String TYPE = "type";
        public static final String ORG_ID = "orgId";
        public static final String APP_ID = "orgId";
        public static final String URL = "url";

    }

    static class Prop {
        public static final String LAST_UPDATE_DATE_TIME = "lastUpdateDateTime";
        public static final String CREATED_DATE_TIME = "createdDateTime";
        public static final String EVENT_NOTIFICATION = "eventNotification";
        public static final String ENABLE_CHANGE_EVENTS = "enableChangeEvents";
    }

    public static final String SURVEY_GROUP_TYPE_SURVEY = "SURVEY";
    public static final String SURVEY_GROUP_TYPE_FOLDER = "FOLDER";

    public static final String LOCK_KIND = "Lock";
    public static final String LOCK_KEY_NAME = "EventPushLock";
    public static final String REQ_ID = "requestId";
    public static final String LOCK_AT = "lockAt";
    public static final long DEFAULT_LOCK_TTL = 3 * 60 * 1000;
    public static long LOCK_TTL = 0;
    public static final double HTTP_DEADLINE = 60d;

    public static final int EVENTS_CHUNK_SIZE = 100;


    static {
        try {
            LOCK_TTL = Long.parseLong(System.getProperties().getProperty("pushLockTtl"));
        } catch (NumberFormatException e) { // noop
        }
    }

    public static class EventTypes {
        public final EntityType type;
        public final String action;

        public EventTypes(EntityType type, String action) {
            this.type = type;
            this.action = action;
        }
    }

    public static EventTypes getEventAndActionType(String kindName) {
        switch (kindName) {
            case Kind.ANSWER:
                return new EventTypes(EntityType.ANSWER, Action.ANSWER);
            case Kind.FORM_INSTANCE:
                return new EventTypes(EntityType.FORM_INSTANCE, Action.FORM_INSTANCE);
            case Kind.DATA_POINT:
                return new EventTypes(EntityType.DATA_POINT, Action.DATA_POINT);
            case Kind.SURVEY_GROUP:
                return new EventTypes(EntityType.SURVEY_GROUP, Action.SURVEY_GROUP);
            case Kind.FORM:
                return new EventTypes(EntityType.FORM, Action.FORM);
            case Kind.QUESTION_GROUP:
                return new EventTypes(EntityType.QUESTION_GROUP, Action.QUESTION_GROUP);
            case Kind.QUESTION:
                return new EventTypes(EntityType.QUESTION, Action.QUESTION);
            case Kind.DEVICE_FILE:
                return new EventTypes(EntityType.DEVICE_FILE, Action.DEVICE_FILE);
        }
        return null;
    }


    public static Map<String, Object> newEvent(String orgId, String eventType,
            Entity entity, Map<String, Object> context) throws AssertionError {

        assert orgId != null : "orgId is required";
        assert eventType != null : "eventType is required";
        assert entity != null : "entity is required";
        assert context != null : "context is required";

        Map<String, Object> result = new HashMap<String, Object>();

        result.put("orgId", orgId);
        result.put("eventType", eventType);
        result.put("entity", entity);
        result.put("context", context);

        return result;
    }

    public static Map<String, Object> newSource(Object principal) {
        Map<String, Object> source = new HashMap<String, Object>();

        if (principal instanceof String) {
            // Tasks related events get an "anonymousUser" as principal
            source.put(Key.TYPE, EventSourceType.SYSTEM);
        } else if (principal instanceof GaeUser) {
            GaeUser usr = (GaeUser) principal;
            source.put(Key.TYPE, EventSourceType.USER);
            source.put(Key.EMAIL, usr.getEmail());
            source.put(Key.ID, usr.getUserId());
        } else {
            log.log(Level.WARNING, "Unable to identify source from authentication principal: "
                    + principal.toString());
        }

        return source;
    }

    public static Map<String, Object> newContext(Date timestamp, Map<String, Object> source) {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put(Key.TIMESTAMP, timestamp);
        context.put(Key.SOURCE, source);
        return context;

    }

    public static Map<String, Object> newEntity(EntityType type, Long id) {
        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put(Key.TYPE, type);
        entity.put(Key.ID, id);
        return entity;
    }

    public static void sendEvents(String urlString, List<Map<String, Object>> events)
            throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url
                .openConnection();

        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type",
                "application/json");

        OutputStreamWriter writer = new OutputStreamWriter(
                connection.getOutputStream());
        objectMapper.writeValue(writer, events);

        System.out.println("    " + connection.getResponseCode());

        writer.close();
        connection.disconnect();

    }

    public static com.google.appengine.api.datastore.Key getLockKey() {
        return KeyFactory.createKey(LOCK_KIND, LOCK_KEY_NAME);
    }

    public static Entity getCurrentLock(DatastoreService ds) {
        Entity current = null;

        try {
            current = ds.get(getLockKey());
        } catch (EntityNotFoundException e) {
            return null;
        }

        Date lockAt = getLockAt(current);
        long now = new Date().getTime();
        long lockTtl = LOCK_TTL == 0 ? DEFAULT_LOCK_TTL : LOCK_TTL;

        if (lockAt != null && ((now - lockAt.getTime()) > lockTtl)) {
            return null;
        }

        return current;
    }

    public static String getReqId(Entity lock) {
        if (lock == null) {
            return null;
        }
        return (String) lock.getProperty(REQ_ID);
    }

    public static Date getLockAt(Entity lock) {
        if (lock == null) {
            return null;
        }
        return (Date) lock.getProperty(LOCK_AT);
    }

    public static boolean newLock(DatastoreService ds, String reqId) {

        Transaction t = ds.beginTransaction(TransactionOptions.Builder.withXG(true));
        try {
            Entity lock = new Entity(getLockKey());
            lock.setProperty(LOCK_AT, new Date());
            lock.setProperty(REQ_ID, reqId);
            ds.put(lock);
            t.commit();
            debug("New lock in ds - reqId: " + reqId);
            return true;
        } catch (Exception e) {
            debug("New lock attempt failed");
        } finally {
            if (t.isActive()) {
                t.rollback();
            }
        }
        return false;
    }

    public static void releaseLock(DatastoreService ds) {
        Transaction t = ds.beginTransaction(TransactionOptions.Builder.withXG(true));
        String reqId = "";
        try {
            Entity lock = ds.get(getLockKey());
            reqId = (String) lock.getProperty(REQ_ID);
            lock.setProperty(LOCK_AT, null);
            lock.setProperty(REQ_ID, null);
            ds.put(lock);
            t.commit();
            debug("Lock release from ds - reqId: " + reqId);
        } catch (Exception e) {
            debug(e.getMessage());
        } finally {
            if (t.isActive()) {
                t.rollback();
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void schedulePush(String id) {

        String reqId = id == null || "".equals(id) ? UUID.randomUUID().toString() : id;

        TaskOptions to = TaskOptions.Builder.withUrl("/app_worker/eventpush").method(Method.GET)
                .param(EventUtils.REQ_ID, reqId)
                .header("host",
                        BackendServiceFactory.getBackendService()
                                .getBackendAddress("dataprocessor"));
        Queue q = QueueFactory.getQueue("background-processing");
        q.add(to);
    }

    private static Filter getFilter() {
        Filter notSynced = new FilterPredicate("synced", FilterOperator.EQUAL, false);
        Filter beforeNow = new FilterPredicate(Prop.CREATED_DATE_TIME, FilterOperator.LESS_THAN,
                new Date());
        return new CompositeFilter(CompositeFilterOperator.AND, Arrays.asList(notSynced,
                beforeNow));
    }

    private static List<Entity> getUnsyncedEvents(DatastoreService ds) {
        Query q = new Query("EventQueue");
        q.setFilter(getFilter()).addSort(Prop.CREATED_DATE_TIME, SortDirection.ASCENDING);
        PreparedQuery pq = ds.prepare(q);

        // FIXME: We'll need to adjust the chunk-size if we're sending answers with signature
        // questions - the limit of the payload size is 10MB
        return pq.asList(com.google.appengine.api.datastore.FetchOptions.Builder
                .withChunkSize(EVENTS_CHUNK_SIZE));
    }

    public static void pushEvents(DatastoreService ds, String reqId, String serviceURL)
            throws IOException {

        URL url = null;
        try {
            url = new URL(serviceURL.trim());
        } catch (MalformedURLException e) {
            log.log(Level.SEVERE, "Events URL not properly defined, check System properties", e);
        }

        if (url == null) {
            return;
        }

        List<Entity> events = getUnsyncedEvents(ds);

        URLFetchService urlfetch = URLFetchServiceFactory.getURLFetchService();
        FetchOptions options = FetchOptions.Builder.followRedirects().setDeadline(HTTP_DEADLINE)
                .validateCertificate();

        HTTPRequest req = new HTTPRequest(url, HTTPMethod.POST, options);
        req.setPayload(objectMapper.writeValueAsBytes(events));


        HTTPResponse resp = urlfetch.fetch(req);

        if (resp.getResponseCode() != SC_OK && resp.getResponseCode() != SC_CREATED) {
            log.severe("Error from event server: " + new String(resp.getContent()));
            throw new RuntimeException("Rescheduling event push");
        }

        if (events.size() == EVENTS_CHUNK_SIZE) {
            // Continue processing events with the same reqId
            schedulePush(reqId);
        }
    }

    // FIXME: Remove me
    public static void debug(String msg) {
        log.log(Level.INFO, Thread.currentThread().getName() + " - " + msg);
    }
}
