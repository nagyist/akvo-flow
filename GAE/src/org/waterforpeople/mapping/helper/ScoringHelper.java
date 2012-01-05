package org.waterforpeople.mapping.helper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.waterforpeople.mapping.domain.AccessPoint;
import org.waterforpeople.mapping.domain.AccessPoint.AccessPointType;

import com.gallatinsystems.common.util.StringUtil;
import com.gallatinsystems.framework.dao.BaseDAO;
import com.gallatinsystems.standards.dao.CompoundStandardDao;
import com.gallatinsystems.standards.dao.StandardDao;
import com.gallatinsystems.standards.domain.CompoundStandard;
import com.gallatinsystems.standards.domain.LevelOfServiceScore;
import com.gallatinsystems.standards.domain.LevelOfServiceScore.ScoreObject;
import com.gallatinsystems.standards.domain.Standard;
import com.gallatinsystems.standards.domain.Standard.StandardComparisons;
import com.gallatinsystems.standards.domain.Standard.StandardScope;
import com.gallatinsystems.standards.domain.Standard.StandardType;
import com.gallatinsystems.standards.domain.Standard.StandardValueType;

public class ScoringHelper {
	private static Logger log = Logger.getLogger(ScoringHelper.class.getName());

	public LevelOfServiceScore scoreWaterPointByLevelOfService(AccessPoint ap,
			StandardType scoreType) {
		LevelOfServiceScore los = new LevelOfServiceScore();
		los.setScoreType(scoreType);
		los.setObjectKey(ap.getKey());
		los.setScoreObject(ScoreObject.AccessPoint);
		BaseDAO<LevelOfServiceScore> losDao = new BaseDAO<LevelOfServiceScore>(
				LevelOfServiceScore.class);
		if (ap.getImprovedWaterPointFlag()) {
			ScoreDetailContainer sdc = scoreStandard(ap, scoreType);
			los.setScore(sdc.getScore());
			if (sdc.getDetails() != null)
				los.setScoreDetails(sdc.getDetails());
			sdc = scoreCompound(ap, scoreType);
			los.setScore(los.getScore() + sdc.getScore());
			if (los.getScoreDetails() != null && sdc.getDetails() != null) {
				ArrayList<String> temp = los.getScoreDetails();
				temp.addAll(sdc.getDetails());
				los.setScoreDetails(temp);
			}

		} else {
			los.setScore(0);
			los.addScoreDetail("0 not improved waterpoint");
		}
		return los;
	}

	private ScoreDetailContainer scoreCompound(AccessPoint ap,
			StandardType scoreType) {
		ScoreDetailContainer sdc = new ScoreDetailContainer();
		Boolean scoreLeft = null;
		Boolean scoreRight = null;
		CompoundStandardDao csdao = new CompoundStandardDao();
		List<CompoundStandard> csList = csdao.listByType(scoreType);
		for (CompoundStandard item : csList) {
			Standard left = item.getStandardLeft();
			Standard right = item.getStandardRight();
			ScoreDetailContainer sdcLeft = processStandard(left, ap);
			ScoreDetailContainer sdcRight = processStandard(right, ap);
			if (item.getOperator().equals(CompoundStandard.Operator.AND)) {
				if (sdcLeft.getScore().equals(1)
						&& sdcRight.getScore().equals(1)) {
					sdc.setScore(sdc.getScore() + 1);
					sdc.add("Plus 1 for compound rule" + item.toString());
				} else {
					sdc.setScore(sdc.getScore() + 0);
					sdc.add("Plus 0 for compound Rule: " + item.toString());
				}
			} else if (item.getOperator().equals(CompoundStandard.Operator.OR)) {
				if (sdcLeft.getScore().equals(1)
						|| sdcRight.getScore().equals(1)) {
					sdc.setScore(sdc.getScore() + 1);
					sdc.add("Plus 1 for compound rule");
				} else {
					sdc.setScore(sdc.getScore() + 0);
					sdc.add("Plus 0 for compound Rule: " + item);
				}
			}
		}
		return sdc;
	}

	private String formScoreDetailMessage(Integer score, String desc,
			String type, String attribute, String value, String operator,
			String compareTo) {
		return "Plus " + score + " for " + desc + " " + type + " " + attribute
				+ " Value " + value + " " + operator + " " + compareTo;

	}

	public void scoreWaterPointSustainability(AccessPoint ap) {

	}

	public void scorePILevelOfService(AccessPoint ap) {

	}

	public void scorePISustainability(AccessPoint ap) {

	}

	private String getAccessPointFieldValue(AccessPoint ap,
			String accessPointAttribute) {
		Method m;
		String value = null;
		try {
			m = AccessPoint.class
					.getMethod(
							"get"
									+ StringUtil
											.capitalizeFirstCharacterString(accessPointAttribute),
							null);
			if (!m.getReturnType().getName().equals("java.lang.String")) {
				value = m.invoke(ap, null).toString();
			} else {
				value = (String) m.invoke(ap, null);
			}
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NullPointerException e) {
			log.info("AccessPoint Attribute: " + accessPointAttribute);
			e.printStackTrace();
			return null;
		}
		return value;
	}

	private Boolean compareBoolean(String uncastvalue, Boolean compareTo) {
		Boolean answer = null;
		Boolean value = Boolean.parseBoolean(uncastvalue);
		if (value.equals(compareTo)) {
			answer = true;
		} else {
			answer = false;
		}
		return answer;
	}

	
	public static void main(String[] args){
	
	}
	
	private Boolean compareStrings(String apvalue, ArrayList<String> valueList,
			StandardComparisons operator) {

		if (operator.equals(StandardComparisons.equal)) {
			Boolean answer = false;
			for (String item : valueList) {
				if (item.trim().equalsIgnoreCase(apvalue.trim())) {
					answer = true;
					break;
				}
			}
			return answer;
		}else if (operator.equals(StandardComparisons.notequal)) {
			Boolean answer = true;
			for (String item : valueList) {
				if (item.trim().equalsIgnoreCase(apvalue.trim())) {
					answer = false;
					break;
				}
			}			
			return answer;
		}
		return null;
	}

	private Boolean compareDouble(
			Standard.StandardComparisons standardComparisons, String apvalue,
			Double compareTo) {
		Boolean answer = false;
		if (standardComparisons.equals(StandardComparisons.lessthan)) {
			if (Double.parseDouble(apvalue) < compareTo) {
				answer = true;
			}
		} else if (standardComparisons
				.equals(StandardComparisons.lessthanorequal)) {
			if (Double.parseDouble(apvalue) <= compareTo) {
				answer = true;
			}
		} else if (standardComparisons.equals(StandardComparisons.greaterthan)) {
			if (Double.parseDouble(apvalue) > compareTo) {
				answer = true;
			}
		} else if (standardComparisons
				.equals(StandardComparisons.greaterthanorequal)) {
			if (Double.parseDouble(apvalue) >= compareTo) {
				answer = true;
			}
		} else if (standardComparisons.equals(StandardComparisons.equal)) {
			if (Double.parseDouble(apvalue) == compareTo) {
				answer = true;
			}
		}
		return answer;
	}

	private class ScoreDetailContainer {
		private Integer score = 0;
		private ArrayList<String> details = null;

		public Integer getScore() {
			return score;
		}

		public void setScore(Integer score) {
			this.score = score;
		}

		public ArrayList<String> getDetails() {
			return details;
		}

		public void setDetails(ArrayList<String> details) {
			this.details = details;
		}

		public void add(String message) {
			if (details == null) {
				details = new ArrayList<String>();
			}
			details.add(message);
		}

	}

	private ScoreDetailContainer scoreStandard(AccessPoint ap,
			StandardType scoreType) {
		ArrayList<String> scoreDetails = new ArrayList<String>();
		Integer score = 0;
		ScoreDetailContainer sdc = new ScoreDetailContainer();
		StandardDao standardDao = new StandardDao();
		List<Standard> standardList = standardDao
				.listByAccessPointTypeAndStandardType(
						AccessPointType.WATER_POINT, scoreType);
		if (standardList != null) {
			for (Standard standard : standardList) {
				sdc = processStandard(standard, ap);
				score = score + sdc.getScore();
				scoreDetails.addAll(sdc.getDetails());
			}
			sdc.setDetails(scoreDetails);
			sdc.setScore(score);

			return sdc;
		}
		return null;
	}

	private ScoreDetailContainer processStandard(Standard standard,
			AccessPoint ap) {
		ScoreDetailContainer sdc = new ScoreDetailContainer();
		Integer score = 0;
		if (standard.getStandardScope().equals(StandardScope.Global)
				|| (standard.getStandardScope().equals(StandardScope.Local))
				&& standard.getCountry().equals(ap.getCountryCode())) {
			String value = getAccessPointFieldValue(ap,
					standard.getAccessPointAttribute());
			if (value == null) {
				sdc.add("Could not score attribute "
						+ standard.getAccessPointAttribute());
				sdc.setScore(0);
			}
			if (standard.getAcessPointAttributeType().equals(
					StandardValueType.Boolean)) {
				sdc = scoreBoolean(standard, value);
			} else if (standard.getAcessPointAttributeType().equals(
					StandardValueType.String)) {
				sdc = scoreString(standard, value);
			} else if (standard.getAcessPointAttributeType().equals(
					StandardValueType.Number)) {
				sdc = scoreDouble(standard, value);
			}
		}
		return sdc;
	}

	private ScoreDetailContainer scoreBoolean(Standard standard, String value) {
		ScoreDetailContainer sdc = new ScoreDetailContainer();
		if (standard.getAcessPointAttributeType().equals(
				StandardValueType.Boolean)) {
			if (compareBoolean(value,
					Boolean.parseBoolean(standard.getPositiveValues().get(0)))) {
				sdc.setScore(sdc.getScore() + 1);
				sdc.add(formScoreDetailMessage(1, standard
						.getStandardDescription(), " WaterPoint ", standard
						.getAccessPointAttribute(), value, standard
						.getStandardComparison().toString(), standard
						.getPositiveValues().toString()));
			} else {
				sdc.setScore(sdc.getScore() + 1);
				sdc.add(formScoreDetailMessage(0, standard
						.getStandardDescription(), " WaterPoint ", standard
						.getAccessPointAttribute(), value, standard
						.getStandardComparison().toString(), standard
						.getPositiveValues().toString()));
			}
		}
		return sdc;
	}

	private ScoreDetailContainer scoreString(Standard standard, String value) {
		ScoreDetailContainer sdc = new ScoreDetailContainer();
		if (standard.getAcessPointAttributeType().equals(
				StandardValueType.String)&&value!=null) {
			if (this.compareStrings(value, standard.getPositiveValues(),standard.getStandardComparison())) {
				sdc.setScore(sdc.getScore() + 1);
				sdc.add(formScoreDetailMessage(1, standard
						.getStandardDescription(), " WaterPoint ", standard
						.getAccessPointAttribute(), value, standard
						.getStandardComparison().toString(), standard
						.getPositiveValues().toString()));
			} else {
				sdc.setScore(sdc.getScore() + 0);
				sdc.add(formScoreDetailMessage(0, standard
						.getStandardDescription(), " WaterPoint ", standard
						.getAccessPointAttribute(), value, standard
						.getStandardComparison().toString(), standard
						.getPositiveValues().toString()));
			}
		}else{
			sdc.setScore(sdc.getScore() + 0);
			sdc.add(formScoreDetailMessage(0, standard
					.getStandardDescription(), " WaterPoint ", standard
					.getAccessPointAttribute(), "Null", standard
					.getStandardComparison().toString(), standard
					.getPositiveValues().toString()));
		}
		return sdc;
	}

	private ScoreDetailContainer scoreDouble(Standard standard, String value) {
		ScoreDetailContainer sdc = new ScoreDetailContainer();
		if (this.compareDouble(standard.getStandardComparison(), value,
				Double.parseDouble(standard.getPositiveValues().get(0)))) {
			sdc.setScore(sdc.getScore() + 1);
			sdc.add(formScoreDetailMessage(1,
					standard.getStandardDescription(), " WaterPoint ", standard
							.getAccessPointAttribute(), value, standard
							.getStandardComparison().toString(), standard
							.getPositiveValues().toString()));
		} else {
			sdc.setScore(sdc.getScore() + 0);
			sdc.add(formScoreDetailMessage(0,
					standard.getStandardDescription(), " WaterPoint ", standard
							.getAccessPointAttribute(), value, standard
							.getStandardComparison().toString(), standard
							.getPositiveValues().toString()));
		}
		return sdc;
	}
}