/*
 *  Copyright (C) 2012 Stichting Akvo (Akvo Foundation)
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
package org.waterforpeople.mapping.app.web.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.waterforpeople.mapping.app.gwt.client.survey.QuestionDto;
import org.waterforpeople.mapping.app.util.DtoMarshaller;
import org.waterforpeople.mapping.app.web.rest.dto.QuestionPayload;
import org.waterforpeople.mapping.app.web.rest.dto.RestStatusDto;

import com.gallatinsystems.common.Constants;
import com.gallatinsystems.framework.exceptions.IllegalDeletionException;
import com.gallatinsystems.survey.dao.QuestionDao;
import com.gallatinsystems.survey.dao.QuestionOptionDao;
import com.gallatinsystems.survey.domain.Question;

@Controller
@RequestMapping("/questions")
public class QuestionRestService {

	@Inject
	private QuestionDao questionDao;

	@Inject
	private QuestionOptionDao questionOptionDao;

	// TODO put in dependencies
	// list all questions
	@RequestMapping(method = RequestMethod.GET, value = "/all")
	@ResponseBody
	public Map<String, List<QuestionDto>> listQuestions() {
		final Map<String, List<QuestionDto>> response = new HashMap<String, List<QuestionDto>>();
		List<QuestionDto> results = new ArrayList<QuestionDto>();
		List<Question> questions = questionDao.list(Constants.ALL_RESULTS);
		if (questions != null) {
			for (Question s : questions) {
				QuestionDto dto = new QuestionDto();
				DtoMarshaller.copyToDto(s, dto);
				dto.setOptionList(questionOptionDao
						.listOptionInStringByQuestion(dto.getKeyId()));
				results.add(dto);
			}
		}
		response.put("questions", results);
		return response;
	}

	// list questions by questionGroup or by survey. If includeNumber or
	// includeOption are true, only NUMBER and OPTION type questions are
	// returned
	// TODO put in dependencies
	@RequestMapping(method = RequestMethod.GET, value = "")
	@ResponseBody
	public Map<String, List<QuestionDto>> listQuestions(
			@RequestParam(value = "questionGroupId", defaultValue = "") Long questionGroupId,
			@RequestParam(value = "surveyId", defaultValue = "") Long surveyId,
			@RequestParam(value = "includeNumber", defaultValue = "") String includeNumber,
			@RequestParam(value = "includeOption", defaultValue = "") String includeOption) {
		final Map<String, List<QuestionDto>> response = new HashMap<String, List<QuestionDto>>();
		List<QuestionDto> results = new ArrayList<QuestionDto>();
		List<Question> questions = new ArrayList<Question>();

		// load questions in a question group, or all questions in the survey
		if (questionGroupId != null) {
			questions = questionDao
					.listQuestionsInOrderForGroup(questionGroupId);
		} else if (surveyId != null) {
			questions = questionDao.listQuestionsInOrder(surveyId);
		}
		if (questions.size() > 0) {
			for (Question question : questions) {

				Boolean includeElement = false;
				// include if we are requesting questions in a group
				if (questionGroupId != null)
					includeElement = true;

				// include if we are requesting questions in a survey, with non
				// of the other parameters set
				if (surveyId != null
						&& (includeNumber == null && includeOption == null))
					includeElement = true;

				// include if we request options, and the present element is an
				// option
				if (surveyId != null && "true".equals(includeOption)
						&& question.getType() == Question.Type.OPTION)
					includeElement = true;

				// include if we request numbers, and the present element is a
				// number
				if (surveyId != null && "true".equals(includeNumber)
						&& question.getType() == Question.Type.NUMBER)
					includeElement = true;

				if (includeElement) {
					QuestionDto dto = new QuestionDto();
					DtoMarshaller.copyToDto(question, dto);
					if (question.getType() == Question.Type.OPTION) {
						dto.setOptionList(questionOptionDao
								.listOptionInStringByQuestion(dto.getKeyId()));
					}
					results.add(dto);
				}
			}
		}

		response.put("questions", results);
		return response;
	}

	// find a single question by the questionId
	// TODO put in dependencies
	@RequestMapping(method = RequestMethod.GET, value = "/{id}")
	@ResponseBody
	public Map<String, QuestionDto> findQuestion(@PathVariable("id") Long id) {
		final Map<String, QuestionDto> response = new HashMap<String, QuestionDto>();
		Question s = questionDao.getByKey(id);
		QuestionDto dto = null;
		if (s != null) {
			dto = new QuestionDto();
			DtoMarshaller.copyToDto(s, dto);
			dto.setOptionList(questionOptionDao
					.listOptionInStringByQuestion(dto.getKeyId()));
		}
		response.put("question", dto);
		return response;

	}

	// delete question by id
	// TODO delete question options
	// TODO delete metricmappings
	@RequestMapping(method = RequestMethod.DELETE, value = "/{id}")
	@ResponseBody
	public Map<String, RestStatusDto> deleteQuestionById(
			@PathVariable("id") Long id) {
		final Map<String, RestStatusDto> response = new HashMap<String, RestStatusDto>();
		Question q = questionDao.getByKey(id);
		RestStatusDto statusDto = null;
		statusDto = new RestStatusDto();
		statusDto.setStatus("failed");

		// check if question exists in the datastore
		if (q != null) {
			// delete question
			try {
				Long questionGroupId = q.getQuestionGroupId();
				Integer order = q.getOrder();
				// first try delete, to see if it is allowed
				questionDao.delete(q);
				// TODO delete options

				List<Question> questions = questionDao
						.listQuestionsInOrderForGroup(questionGroupId);
				if (questions != null) {
					for (Question question : questions) {
						if (question.getOrder() > order) {
							question.setOrder(question.getOrder() - 1);
							question = questionDao.save(question);
						}
					}
				}
				statusDto.setStatus("ok");
			} catch (IllegalDeletionException e) {
				statusDto.setStatus("failed");
				statusDto.setMessage(e.getMessage());
			}
		}
		response.put("meta", statusDto);
		return response;
	}

	// update existing question
	// TODO put in dependencies
	// TODO put in metrics
	@RequestMapping(method = RequestMethod.PUT, value = "/{id}")
	@ResponseBody
	public Map<String, Object> saveExistingQuestion(
			@RequestBody QuestionPayload payLoad) {
		final QuestionDto questionDto = payLoad.getQuestion();
		final Map<String, Object> response = new HashMap<String, Object>();
		QuestionDto dto = null;

		RestStatusDto statusDto = new RestStatusDto();
		statusDto.setStatus("failed");

		// if the POST data contains a valid questionDto, continue. Otherwise,
		// server will respond with 400 Bad Request
		if (questionDto != null) {
			Long keyId = questionDto.getKeyId();
			Question q;

			// if the questionDto has a key, try to get the question.
			if (keyId != null) {
				q = questionDao.getByKey(keyId);
				// if we find the question, update it's properties
				if (q != null) {

					Integer origOrder = q.getOrder();
					// copy the properties, except the createdDateTime property,
					// because it is set in the Dao.
					BeanUtils.copyProperties(questionDto, q, new String[] {
							"createdDateTime", "order", "type", "optionList" });
					if (questionDto.getType() != null)
						q.setType(Question.Type.valueOf(questionDto.getType()
								.toString()));

					questionOptionDao.saveOptionInStringByQuestion(keyId,
							questionDto.getOptionList());

					q = questionDao.save(q);
					// TODO save options list

					// if the original order is different from the current
					// number in the order field interpret the number as
					// an 'afterInsert' number and adapt the order of all the
					// question groups. If not, the order field does not need to
					// be copied as it has not changed.
					if (origOrder != questionDto.getOrder()) {
						Integer insertAfterOrder = questionDto.getOrder();
						Integer currentOrder;
						Boolean movingUp = (origOrder < insertAfterOrder);
						List<Question> questions = questionDao
								.listQuestionsInOrderForGroup(q
										.getQuestionGroupId());
						if (questions != null) {
							for (Question question : questions) {
								currentOrder = question.getOrder();
								if (movingUp) {
									// move moving item to right location
									if (currentOrder == origOrder) {
										question.setOrder(insertAfterOrder);
										question = questionDao.save(question);
									} else if ((currentOrder > origOrder)
											&& (currentOrder <= insertAfterOrder)) {
										// move down
										question.setOrder(question.getOrder() - 1);
										question = questionDao.save(question);
									}
								} else {
									// Moving down
									if (currentOrder == origOrder) {
										question.setOrder(insertAfterOrder + 1);
										question = questionDao.save(question);
									} else if ((currentOrder < origOrder)
											&& (currentOrder > insertAfterOrder)) {
										// move up
										question.setOrder(question.getOrder() + 1);
										question = questionDao.save(question);
									}
								}
							}
						}
					}

					// get question again, as it's order might have changed
					q = questionDao.getByKey(keyId);
					dto = new QuestionDto();
					DtoMarshaller.copyToDto(q, dto);
					dto.setOptionList(questionOptionDao
							.listOptionInStringByQuestion(dto.getKeyId()));
					statusDto.setStatus("ok");
				}
			}
		}
		response.put("meta", statusDto);
		response.put("question", dto);
		return response;
	}

	// create new question
	// TODO put in dependencies
	// TODO put in metrics
	@RequestMapping(method = RequestMethod.POST, value = "")
	@ResponseBody
	public Map<String, Object> saveNewQuestion(
			@RequestBody QuestionPayload payLoad) {
		final QuestionDto questionDto = payLoad.getQuestion();
		final Map<String, Object> response = new HashMap<String, Object>();
		QuestionDto dto = null;

		RestStatusDto statusDto = new RestStatusDto();
		statusDto.setStatus("failed");

		// if the POST data contains a valid questionDto, continue. Otherwise,
		// server will respond with 400 Bad Request
		if (questionDto != null) {
			Question q = new Question();

			// copy the properties, except the createdDateTime property, because
			// it is set in the Dao.
			BeanUtils.copyProperties(questionDto, q, new String[] {
					"createdDateTime", "order", "type", "optionList" });
			if (questionDto.getType() != null)
				q.setType(Question.Type.valueOf(questionDto.getType()
						.toString()));

			// make room by moving items up
			List<Question> questions = questionDao
					.listQuestionsInOrderForGroup(questionDto
							.getQuestionGroupId());
			Integer insertAfterOrder = questionDto.getOrder();
			if (questions != null) {
				for (Question question : questions) {
					if (question.getOrder() > insertAfterOrder) {
						question.setOrder(question.getOrder() + 1);
						question = questionDao.save(question);
					}
				}
			}

			q.setOrder(insertAfterOrder + 1);
			q = questionDao.save(q);

			dto = new QuestionDto();
			DtoMarshaller.copyToDto(q, dto);
			statusDto.setStatus("ok");
		}

		response.put("meta", statusDto);
		response.put("question", dto);
		return response;
	}

}
