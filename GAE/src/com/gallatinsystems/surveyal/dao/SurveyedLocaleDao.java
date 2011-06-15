package com.gallatinsystems.surveyal.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jdo.PersistenceManager;

import com.gallatinsystems.framework.dao.BaseDAO;
import com.gallatinsystems.framework.servlet.PersistenceFilter;
import com.gallatinsystems.surveyal.domain.SurveyalValue;
import com.gallatinsystems.surveyal.domain.SurveyedLocale;

/**
 * Data access object for manipulating SurveyedLocales
 * 
 * @author Christopher Fagiani
 * 
 */
public class SurveyedLocaleDao extends BaseDAO<SurveyedLocale> {

	public SurveyedLocaleDao() {
		super(SurveyedLocale.class);
	}

	/**
	 * lists the set of SurveyedLocales that are within tolerance of the lat/lon
	 * coordinates passed in.
	 * 
	 * @param lat
	 * @param lon
	 * @param tolerance
	 * @return
	 */
	public List<SurveyedLocale> listLocalesByCoordinates(String pointType, double lat,
			double lon, double tolerance) {
		return listLocalesByCoordinates(pointType, lat-tolerance, lon-tolerance, lat+tolerance, lon+tolerance,CURSOR_TYPE.all.toString(),null);
	}
	
	/**
	 * lists locales that fit within the bounding box passed in
	 * @param pointType
	 * @param lat1
	 * @param lon1
	 * @param lat2
	 * @param lon2
	 * @return
	 */
	@SuppressWarnings("unchecked")	
	public List<SurveyedLocale> listLocalesByCoordinates(String pointType, Double lat1,
			Double lon1, Double lat2,Double lon2, String cursor,Integer pageSize) {
		PersistenceManager pm = PersistenceFilter.getManager();
		javax.jdo.Query query = pm.newQuery(SurveyedLocale.class);
		Map<String, Object> paramMap = null;

		StringBuilder filterString = new StringBuilder();
		StringBuilder paramString = new StringBuilder();
		paramMap = new HashMap<String, Object>();

		appendNonNullParam("localeType",filterString,paramString,"String",pointType,paramMap);
		appendNonNullParam("latitude", filterString, paramString, "Double", lat1, paramMap, GTE_OP);
		appendNonNullParam("latitude", filterString, paramString, "Double", lat2,paramMap, LTE_OP);

		query.setFilter(filterString.toString());
		query.declareParameters(paramString.toString());
		prepareCursor(cursor,pageSize, query);
		List<SurveyedLocale> candidates = (List<SurveyedLocale>) query
				.executeWithMap(paramMap);
		// since the datastore only supports an inequality check on a single
		// parameter at a time, only look at LAT in the query. Filter on Lon
		// afterwards.
		List<SurveyedLocale> results = new ArrayList<SurveyedLocale>();
		if (candidates != null) {
			for (SurveyedLocale l : candidates) {
				if (l.getLongitude() > (lon1)
						&& l.getLongitude() < (lon2)) {
					results.add(l);
				}
			}
		}
		return results;
	}
	
	/**
	 * lists all locales that match the geo constraints passed in
	 * 
	 * @param countryCode
	 * @param level
	 * @param subValue
	 * @param type
	 * @param org
	 * @param cursor
	 * @param desiredResults
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<SurveyedLocale> listBySubLevel(String countryCode,
			Integer level, String subValue, String type, String org,
			String cursor, Integer desiredResults) {
		PersistenceManager pm = PersistenceFilter.getManager();
		javax.jdo.Query query = pm.newQuery(SurveyedLocale.class);
		StringBuilder filterString = new StringBuilder();
		StringBuilder paramString = new StringBuilder();
		Map<String, Object> paramMap = null;
		paramMap = new HashMap<String, Object>();

		appendNonNullParam("localeType", filterString, paramString, "String",
				type, paramMap);
		appendNonNullParam("countryCode", filterString, paramString, "String",
				countryCode, paramMap);
		appendNonNullParam("organization", filterString, paramString, "String",
				org, paramMap);
		if (level != null && level > 0 && level <= 6) {
			appendNonNullParam("sublevel" + level, filterString, paramString,
					"String", subValue, paramMap);
		}
		query.setFilter(filterString.toString());
		query.declareParameters(paramString.toString());
		prepareCursor(cursor, desiredResults, query);
		List<SurveyedLocale> results = (List<SurveyedLocale>) query
				.executeWithMap(paramMap);
		return results;
	}

	/**
	 * searches for surveyedLocale based on params passed in
	 * 
	 * 
	 * 
	 * @param country
	 * @param collDateFrom
	 * @param collDateTo
	 * @param type
	 * @param metricId
	 * @param metricValue
	 * @param orderByField
	 * @param orderByDir
	 * @param pageSize
	 * @param cursorString
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<SurveyedLocale> search(String country, Date collDateFrom,
			Date collDateTo, String type, String orderByField,
			String orderByDir, Integer pageSize, String cursorString) {

		PersistenceManager pm = PersistenceFilter.getManager();
		javax.jdo.Query query = pm.newQuery(SurveyedLocale.class);
		StringBuilder filterString = new StringBuilder();
		StringBuilder paramString = new StringBuilder();
		Map<String, Object> paramMap = new HashMap<String, Object>();

		appendNonNullParam("countryCode", filterString, paramString, "String",
				country, paramMap);

		appendNonNullParam("localeType", filterString, paramString, "String",
				type, paramMap);

		appendNonNullParam("lastSurveyedDate", filterString, paramString,
				"Date", collDateFrom, paramMap, GTE_OP);
		appendNonNullParam("lastSurveyedDate", filterString, paramString,
				"Date", collDateTo, paramMap, LTE_OP);

		if (orderByField != null) {
			String ordering = orderByDir;
			if (ordering == null) {
				ordering = "asc";
			}
			query.setOrdering(orderByField + " " + ordering);
		}
		if (filterString.length() > 0) {
			query.setFilter(filterString.toString());
			query.declareParameters(paramString.toString());
		}
		prepareCursor(cursorString, pageSize, query);
		if (collDateFrom != null || collDateTo != null) {
			query.declareImports("import java.util.Date");
			if (orderByField != null
					&& !orderByField.trim().equals("lastSurveyedDate")) {
				query.setOrdering("lastSurveyedDate "
						+ (orderByDir != null ? orderByDir : "asc"));
			}
		}
		return (List<SurveyedLocale>) query.executeWithMap(paramMap);
	}

	/**
	 * returns all the SurveyalValues corresponding to the metric id/value pair
	 * passed in
	 * 
	 * @param metricId
	 * @param metricValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<SurveyalValue> listSurveyalValueByMetric(Long metricId,
			String metricValue, Integer pageSize, String cursor) {
		PersistenceManager pm = PersistenceFilter.getManager();
		javax.jdo.Query query = pm.newQuery(SurveyalValue.class);
		StringBuilder filterString = new StringBuilder();
		StringBuilder paramString = new StringBuilder();
		Map<String, Object> paramMap = new HashMap<String, Object>();

		appendNonNullParam("metricId", filterString, paramString, "Long",
				metricId, paramMap);

		appendNonNullParam("stringValue", filterString, paramString, "String",
				metricValue, paramMap);
		query.setFilter(filterString.toString());
		query.declareParameters(paramString.toString());
		prepareCursor(cursor, pageSize, query);
		return (List<SurveyalValue>) query.executeWithMap(paramMap);

	}

	/**
	 * lists all values for a given survey instance
	 * 
	 * @param surveyInstanceId
	 * @return
	 */
	public List<SurveyalValue> listSurveyalValuesByInstance(
			Long surveyInstanceId) {
		return listByProperty("surveyInstanceId", surveyInstanceId, "Long",
				"questionText, metricName asc", SurveyalValue.class);
	}

	/**
	 * returns all the locales with the identifier passed in. If needDetails is
	 * true, it will list the surveyalValues for the locale
	 * 
	 * @param identifier
	 * @param needDetails
	 * @return
	 */
	public List<SurveyedLocale> listLocalesByCode(String identifier,
			boolean needDetails) {
		List<SurveyedLocale> locales = listByProperty("identifier", identifier,
				"String");
		if (locales != null && needDetails) {
			for (SurveyedLocale l : locales) {
				l.setSurveyalValues(listByProperty("surveyedLocaleId", l
						.getKey().getId(), "Long", SurveyalValue.class));
			}
		}
		return locales;
	}	
}
