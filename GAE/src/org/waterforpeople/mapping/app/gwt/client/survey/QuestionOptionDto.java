package org.waterforpeople.mapping.app.gwt.client.survey;

import java.io.Serializable;

import com.gallatinsystems.framework.gwt.dto.client.BaseDto;

public class QuestionOptionDto extends BaseDto implements Serializable {
	
	private static final long serialVersionUID = 6237222655812167675L;
	
	private String text;
	private String code;
	private Integer order;
	
	public Integer getOrder() {
		return order;
	}
	public void setOrder(Integer order) {
		this.order = order;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	
}
