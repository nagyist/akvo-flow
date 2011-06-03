package org.waterforpeople.mapping.app.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheException;
import net.sf.jsr107cache.CacheFactory;
import net.sf.jsr107cache.CacheManager;

import org.json.JSONObject;
import org.waterforpeople.mapping.app.gwt.client.location.PlacemarkDto;
import org.waterforpeople.mapping.app.web.dto.PlacemarkRestRequest;
import org.waterforpeople.mapping.app.web.dto.PlacemarkRestResponse;
import org.waterforpeople.mapping.dao.AccessPointDao;
import org.waterforpeople.mapping.domain.AccessPoint;
import org.waterforpeople.mapping.domain.AccessPoint.AccessPointType;
import org.waterforpeople.mapping.domain.AccessPoint.Status;

import com.gallatinsystems.framework.rest.AbstractRestApiServlet;
import com.gallatinsystems.framework.rest.RestRequest;
import com.gallatinsystems.framework.rest.RestResponse;
import com.gallatinsystems.surveyal.dao.SurveyedLocaleDao;
import com.gallatinsystems.surveyal.domain.SurveyedLocale;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.jsr107cache.GCacheFactory;

public class PlacemarkServlet extends AbstractRestApiServlet {
	private static final long serialVersionUID = -9031594440737716966L;
	private static final Logger log = Logger.getLogger(PlacemarkServlet.class
			.getName());
	private static final String AP_DOMAIN = "AccessPoint";

	private KMLGenerator kmlGen = new KMLGenerator();
	private Cache cache;
	private AccessPointDao apDao;
	private SurveyedLocaleDao localeDao;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public PlacemarkServlet() {
		super();
		apDao = new AccessPointDao();
		localeDao = new SurveyedLocaleDao();
		CacheFactory cacheFactory;
		try {
			cacheFactory = CacheManager.getInstance().getCacheFactory();
			Map configMap = new HashMap();
			configMap.put(GCacheFactory.EXPIRATION_DELTA, 3600);
			configMap.put(MemcacheService.SetPolicy.SET_ALWAYS, true);
			cache = cacheFactory.createCache(Collections.emptyMap());
		} catch (CacheException e) {
			log.log(Level.SEVERE, "Could not initialize cache", e);

		}
	}

	@Override
	protected RestRequest convertRequest() throws Exception {
		HttpServletRequest req = getRequest();
		RestRequest restRequest = new PlacemarkRestRequest();
		restRequest.populateFromHttpRequest(req);
		return restRequest;
	}

	@Override
	protected RestResponse handleRequest(RestRequest req) throws Exception {
		PlacemarkRestRequest piReq = (PlacemarkRestRequest) req;
		if (cache != null && !piReq.getIgnoreCache()) {
			PlacemarkRestResponse cachedResponse = null;
			try {
				log.log(Level.INFO,
						"Checking Cache for: " + piReq.getCacheKey());
				cachedResponse = (PlacemarkRestResponse) cache.get(piReq
						.getCacheKey());
			} catch (Throwable t) {
				log.log(Level.WARNING, "Could not look up data in cache", t);
			}
			if (cachedResponse != null) {
				return cachedResponse;
			}
		}
		PlacemarkRestResponse response = null;
		// if we had a cache miss (or the cache is not available), then hit the
		// datastore and cachethe resupt
		if (piReq.getAction() != null
				&& PlacemarkRestRequest.GET_AP_DETAILS_ACTION.equals(piReq
						.getAction())) {
			List<AccessPoint> apList = new ArrayList<AccessPoint>();
			if (piReq.getCommunityCode() != null
					&& piReq.getCommunityCode().trim().length() > 0) {
				AccessPoint ap = (AccessPoint) apDao.findAccessPoint(
						piReq.getCommunityCode(), piReq.getPointType());
				apList.add(ap);
			}
			response = (PlacemarkRestResponse) convertToResponse(apList, true,
					null, null, piReq.getDisplay());

		} else {
			// ListPlacemarks Action
			if (piReq.getAction() == null) {

				int desiredResults = 20;
				if (piReq.getDesiredResults() > 20) {
					if (piReq.getDesiredResults() > 500) {
						desiredResults = 500;
					} else {
						desiredResults = piReq.getDesiredResults();
					}
				}

				if (piReq.getSubLevel() != null) {
					if (piReq.getDomain() == null
							|| AP_DOMAIN.equalsIgnoreCase(piReq.getDomain())) {
						List<AccessPoint> results = apDao.listBySubLevel(
								piReq.getCountry(), piReq.getSubLevel(),
								piReq.getSubLevelValue(), piReq.getCursor(),
								AccessPointType.WATER_POINT, desiredResults);
						String display = null;
						if (piReq.getDisplay() != null) {
							display = piReq.getDisplay();
						}
						response = (PlacemarkRestResponse) convertToResponse(
								results, piReq.getNeedDetailsFlag(),
								AccessPointDao.getCursor(results),
								piReq.getCursor(), display);
					}else{
						//TODO: add localeType to param
						//TODO: add organization to param
						List<SurveyedLocale> results = localeDao.listBySubLevel(
								piReq.getCountry(), piReq.getSubLevel(),
								piReq.getSubLevelValue(), null,null,piReq.getCursor(),
								desiredResults);
						String display = null;
						if (piReq.getDisplay() != null) {
							display = piReq.getDisplay();
						}
						response = (PlacemarkRestResponse) convertLocaleToResponse(
								results, piReq.getNeedDetailsFlag(),
								AccessPointDao.getCursor(results),
								piReq.getCursor(), display);
					}
				} else {
					if (piReq.getDomain() == null
							|| AP_DOMAIN.equalsIgnoreCase(piReq.getDomain())) {
					List<AccessPoint> results = apDao.searchAccessPoints(
							piReq.getCountry(), null, null, null, null, null,
							null, null, null, null, desiredResults,
							piReq.getCursor());
					String display = null;
					if (piReq.getDisplay() != null) {
						display = piReq.getDisplay();
					}
					response = (PlacemarkRestResponse) convertToResponse(
							results, piReq.getNeedDetailsFlag(),
							AccessPointDao.getCursor(results),
							piReq.getCursor(), display);
					}else{
						List<SurveyedLocale> results = localeDao.listBySubLevel(
								piReq.getCountry(), null,null, null,null,piReq.getCursor(),
								desiredResults);
						String display = null;
						if (piReq.getDisplay() != null) {
							display = piReq.getDisplay();
						}
						response = (PlacemarkRestResponse) convertLocaleToResponse(
								results, piReq.getNeedDetailsFlag(),
								AccessPointDao.getCursor(results),
								piReq.getCursor(), display);
					}
				}

			} else if (piReq.getAction().equals(
					PlacemarkRestRequest.LIST_BOUNDING_BOX_ACTION)
					&& piReq.getLat1() != null) {
				Integer maxResults = 20;
				if (piReq.getDesiredResults() > 20
						&& piReq.getDesiredResults() <= 500) {
					maxResults = piReq.getDesiredResults();
				}
				List<AccessPoint> results = apDao
						.listAccessPointsByBoundingBox(piReq.getPointType(),
								piReq.getLat1(), piReq.getLat2(),
								piReq.getLong1(), piReq.getLong2(),
								piReq.getCursor(), maxResults);
				response = (PlacemarkRestResponse) convertToResponse(results,
						piReq.getNeedDetailsFlag(),
						AccessPointDao.getCursor(results), piReq.getCursor(),
						piReq.getDisplay());
			}

		}
		if (response != null && cache != null) {
			try {
				cache.put(piReq.getCacheKey(), response);
			} catch (Throwable t) {
				log.log(Level.WARNING, "Could not cache results", t);
			}
		}
		return response;
	}

	private RestResponse convertToResponse(List<AccessPoint> apList,
			Boolean needDetailsFlag, String cursor, String oldCursor,
			String display) {
		PlacemarkRestResponse resp = new PlacemarkRestResponse();
		if (needDetailsFlag == null)
			needDetailsFlag = true;
		if (apList != null) {
			List<PlacemarkDto> dtoList = new ArrayList<PlacemarkDto>();

			for (AccessPoint ap : apList) {
				if (!ap.getPointType().equals(AccessPointType.SANITATION_POINT)) {
					dtoList.add(marshallDomainToDto(ap, needDetailsFlag,
							display));
				}
				resp.setPlacemarks(dtoList);
			}
		}
		if (cursor != null) {
			if (oldCursor == null || !cursor.equals(oldCursor)) {
				resp.setCursor(cursor);
			}
		} else {
			resp.setCursor(null);
		}
		return resp;
	}
	
	private RestResponse convertLocaleToResponse(List<SurveyedLocale> localeList,
			Boolean needDetailsFlag, String cursor, String oldCursor,
			String display) {
		PlacemarkRestResponse resp = new PlacemarkRestResponse();
		if (needDetailsFlag == null)
			needDetailsFlag = true;
		if (localeList != null) {
			List<PlacemarkDto> dtoList = new ArrayList<PlacemarkDto>();
			for (SurveyedLocale ap : localeList) {				
					dtoList.add(marshallDomainToDto(ap, needDetailsFlag,
							display));				
				resp.setPlacemarks(dtoList);
			}
		}
		if (cursor != null) {
			if (oldCursor == null || !cursor.equals(oldCursor)) {
				resp.setCursor(cursor);
			}
		} else {
			resp.setCursor(null);
		}
		return resp;
	}
	

	private PlacemarkDto marshallDomainToDto(SurveyedLocale ap,
			Boolean needDetailsFlag, String display) {
		PlacemarkDto pdto = new PlacemarkDto();
		//TODO: encode type and status properly
		pdto.setPinStyle(KMLGenerator.encodePinStyle(AccessPointType.WATER_POINT,
				AccessPoint.Status.FUNCTIONING_HIGH));
		pdto.setIconUrl(getUrlFromStatus(AccessPoint.Status.FUNCTIONING_HIGH, AccessPointType.WATER_POINT));
		pdto.setMarkType(AccessPointType.WATER_POINT.toString());
		
		pdto.setLatitude(ap.getLatitude());
		pdto.setLongitude(ap.getLongitude());		
		pdto.setCommunityCode(ap.getIdentifier());		
		pdto.setCollectionDate(ap.getLastUpdateDateTime());
		//TODO: implement details
		if (needDetailsFlag) {
			String placemarkString = null;
			try {
				/*placemarkString = kmlGen.bindPlacemark(ap,
						"placemarkExternalMap.vm", display);*/
				placemarkString = "<div><h3>Coming Soon</h3></div>";
				pdto.setPlacemarkContents(placemarkString);
			} catch (Exception e) {
				log.log(Level.SEVERE, "Could not bind placemarks", e);
			}
		}
		return pdto;
	}

	private PlacemarkDto marshallDomainToDto(AccessPoint ap,
			Boolean needDetailsFlag, String display) {
		PlacemarkDto pdto = new PlacemarkDto();
		pdto.setPinStyle(KMLGenerator.encodePinStyle(ap.getPointType(),
				ap.getPointStatus()));
		pdto.setLatitude(ap.getLatitude());
		pdto.setLongitude(ap.getLongitude());
		pdto.setIconUrl(getUrlFromStatus(ap.getPointStatus(), ap.getPointType()));
		pdto.setCommunityCode(ap.getCommunityCode());
		pdto.setMarkType(ap.getPointType().toString());
		pdto.setCollectionDate(ap.getCollectionDate());
		if (needDetailsFlag) {
			String placemarkString = null;
			try {
				placemarkString = kmlGen.bindPlacemark(ap,
						"placemarkExternalMap.vm", display);
				pdto.setPlacemarkContents(placemarkString);
			} catch (Exception e) {
				log.log(Level.SEVERE, "Could not bind placemarks", e);
			}
		}
		return pdto;
	}

	private String getUrlFromStatus(Status status,
			AccessPoint.AccessPointType pointType) {
		if (status == null) {
			return "Unknown";
		}
		if (AccessPointType.WATER_POINT.equals(pointType)) {
			if (status.equals(AccessPoint.Status.FUNCTIONING_HIGH)) {
				return KMLGenerator.WATER_POINT_FUNCTIONING_GREEN_ICON_URL;
			} else if (status.equals(AccessPoint.Status.FUNCTIONING_OK)
					|| status
							.equals(AccessPoint.Status.FUNCTIONING_WITH_PROBLEMS)) {
				return KMLGenerator.WATER_POINT_FUNCTIONING_YELLOW_ICON_URL;
			} else if (status.equals(AccessPoint.Status.BROKEN_DOWN)) {
				return KMLGenerator.WATER_POINT_FUNCTIONING_RED_ICON_URL;
			} else if (status.equals(AccessPoint.Status.NO_IMPROVED_SYSTEM)) {
				return KMLGenerator.WATER_POINT_FUNCTIONING_BLACK_ICON_URL;
			} else {
				return KMLGenerator.WATER_POINT_FUNCTIONING_BLACK_ICON_URL;
			}
		} else if (AccessPointType.PUBLIC_INSTITUTION.equals(pointType)) {
			if (status.equals(AccessPoint.Status.FUNCTIONING_HIGH)) {
				return KMLGenerator.PUBLIC_INSTITUTION_FUNCTIONING_GREEN_ICON_URL;
			} else if (status.equals(AccessPoint.Status.FUNCTIONING_OK)
					|| status
							.equals(AccessPoint.Status.FUNCTIONING_WITH_PROBLEMS)) {
				return KMLGenerator.PUBLIC_INSTITUTION_FUNCTIONING_YELLOW_ICON_URL;
			} else if (status.equals(AccessPoint.Status.BROKEN_DOWN)) {
				return KMLGenerator.PUBLIC_INSTITUTION_FUNCTIONING_RED_ICON_URL;
			} else if (status.equals(AccessPoint.Status.NO_IMPROVED_SYSTEM)) {
				return KMLGenerator.PUBLIC_INSTITUTION_FUNCTIONING_BLACK_ICON_URL;
			} else {
				return KMLGenerator.PUBLIC_INSTITUTION_FUNCTIONING_BLACK_ICON_URL;
			}
		} else if (AccessPointType.SCHOOL.equals(pointType)) {
			if (status.equals(AccessPoint.Status.FUNCTIONING_HIGH)) {
				return KMLGenerator.SCHOOL_INSTITUTION_FUNCTIONING_GREEN_ICON_URL;
			} else if (status.equals(AccessPoint.Status.FUNCTIONING_OK)
					|| status
							.equals(AccessPoint.Status.FUNCTIONING_WITH_PROBLEMS)) {
				return KMLGenerator.SCHOOL_INSTITUTION_FUNCTIONING_YELLOW_ICON_URL;
			} else if (status.equals(AccessPoint.Status.BROKEN_DOWN)) {
				return KMLGenerator.SCHOOL_INSTITUTION_FUNCTIONING_RED_ICON_URL;
			} else if (status.equals(AccessPoint.Status.NO_IMPROVED_SYSTEM)) {
				return KMLGenerator.SCHOOL_INSTITUTION_FUNCTIONING_BLACK_ICON_URL;
			} else {
				return KMLGenerator.SCHOOL_INSTITUTION_FUNCTIONING_BLACK_ICON_URL;
			}
		}
		return null;

	}

	@Override
	protected void writeOkResponse(RestResponse resp) throws Exception {
		getResponse().setStatus(200);
		PlacemarkRestResponse piResp = (PlacemarkRestResponse) resp;
		JSONObject result = new JSONObject(piResp);
		getResponse().getWriter().println(result.toString());
	}
}
