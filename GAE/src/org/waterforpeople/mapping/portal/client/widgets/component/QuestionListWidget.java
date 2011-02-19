package org.waterforpeople.mapping.portal.client.widgets.component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.waterforpeople.mapping.app.gwt.client.survey.QuestionDto;
import org.waterforpeople.mapping.app.gwt.client.survey.QuestionGroupDto;
import org.waterforpeople.mapping.app.gwt.client.survey.SurveyService;
import org.waterforpeople.mapping.app.gwt.client.survey.SurveyServiceAsync;

import com.gallatinsystems.framework.gwt.component.ListBasedWidget;
import com.gallatinsystems.framework.gwt.component.PageController;
import com.gallatinsystems.framework.gwt.util.client.MessageDialog;
import com.gallatinsystems.framework.gwt.wizard.client.CompletionListener;
import com.gallatinsystems.framework.gwt.wizard.client.ContextAware;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/**
 * Widget for viewing question lists
 * 
 * TODO: handle delete and reorder
 * 
 * @author Christopher Fagiani
 * 
 */
public class QuestionListWidget extends ListBasedWidget implements ContextAware {

	private SurveyServiceAsync surveyService;
	private QuestionDto selectedQuestion;
	private QuestionGroupDto questionGroup;
	private Map<String, Object> bundle;
	private Map<Widget, Integer> widgetRowMap;
	private Grid dataGrid;

	public QuestionListWidget(PageController controller) {
		super(controller);
		bundle = new HashMap<String, Object>();
		surveyService = GWT.create(SurveyService.class);
		selectedQuestion = null;
	}

	public void loadData(QuestionGroupDto questionGroupDto) {
		if (questionGroupDto != null) {
			if (questionGroupDto.getQuestionMap() != null
					&& questionGroupDto.getQuestionMap().size() > 0) {
				populateQuestionList(questionGroupDto.getQuestionMap().values());
			} else {
				surveyService.listQuestionsByQuestionGroup(questionGroupDto
						.getKeyId().toString(), false,
						new AsyncCallback<ArrayList<QuestionDto>>() {

							@Override
							public void onFailure(Throwable caught) {
								toggleLoading(false);
							}

							@Override
							public void onSuccess(ArrayList<QuestionDto> result) {
								if (result != null && result.size() > 0) {
									TreeMap<Integer, QuestionDto> questionTree = new TreeMap<Integer, QuestionDto>();

									for (int i = 0; i < result.size(); i++) {
										questionTree.put(result.get(i)
												.getOrder(), result.get(i));
									}
									populateQuestionList(result);
									questionGroup.setQuestionMap(questionTree);
									bundle.put(
											BundleConstants.QUESTION_GROUP_KEY,
											questionGroup);

								} else {
									toggleLoading(false);
								}
							}
						});
			}
		}
	}

	private void populateQuestionList(Collection<QuestionDto> questionList) {
		toggleLoading(false);
		widgetRowMap = new HashMap<Widget, Integer>();
		if (dataGrid != null) {
			dataGrid.removeFromParent();
		}
		dataGrid = new Grid(questionList.size(), 4);
		int i = 0;
		if (questionList != null) {
			for (QuestionDto q : questionList) {
				Label l = createListEntry(q.getText());
				HorizontalPanel bp = new HorizontalPanel();

				Image moveUp = new Image("/images/greenuparrow.png");
				if (i > 0) {
					createClickableWidget(ClickMode.MOVE_UP, moveUp);
				}
				Image moveDown = new Image("/images/greendownarrow.png");
				if (i < questionList.size() - 1) {
					createClickableWidget(ClickMode.MOVE_DOWN, moveDown);
				}
				Button deleteButton = createButton(ClickMode.DELETE, "Delete");
				Button editButton = createButton(ClickMode.EDIT, "Edit");

				bp.add(moveUp);
				bp.add(moveDown);

				widgetRowMap.put(l, i);
				dataGrid.setWidget(i, 0, l);
				dataGrid.setWidget(i, 1, bp);
				dataGrid.setWidget(i, 2, editButton);
				dataGrid.setWidget(i, 3, deleteButton);

				widgetRowMap.put(editButton, i);
				widgetRowMap.put(deleteButton, i);
				widgetRowMap.put(moveUp, i);
				widgetRowMap.put(moveDown, i);

				i++;
			}
		}
		addWidget(dataGrid);
	}

	@Override
	public void setContextBundle(Map<String, Object> bundle) {
		this.bundle = bundle;
		// remove the bundle item that this widget populates
		bundle.remove(BundleConstants.QUESTION_KEY);
		questionGroup = (QuestionGroupDto) bundle
				.get(BundleConstants.QUESTION_GROUP_KEY);
		loadData(questionGroup);
	}

	@Override
	protected void handleItemClick(Object source, ClickMode mode) {
		int i = 0;
		for (QuestionDto q : questionGroup.getQuestionMap().values()) {
			if (i == widgetRowMap.get((Widget) source)) {
				selectedQuestion = q;
				break;
			}
			i++;
		}
		if (ClickMode.EDIT == mode || ClickMode.OPEN == mode) {
			openPage(QuestionEditWidget.class, getContextBundle(true));
		} else if (ClickMode.DELETE == mode) {
			deleteQuestion(selectedQuestion);
		} else if (ClickMode.MOVE_DOWN == mode) {
			moveQuestion(1, selectedQuestion);
		} else if (ClickMode.MOVE_UP == mode) {
			moveQuestion(-1, selectedQuestion);
		}
	}

	private void moveQuestion(int increment, QuestionDto question) {
		setWorking(true);
		final MessageDialog savingDialog = new MessageDialog("Saving order",
				"Please wait", true);
		savingDialog.showCentered();
		Integer prevIdx = null;
		Integer nextIdx = null;
		Integer idx = null;
		for (Entry<Integer, QuestionDto> entry : questionGroup.getQuestionMap()
				.entrySet()) {
			if (idx != null) {
				nextIdx = entry.getKey();
				break;
			}
			if (entry.getValue().getKeyId().equals(question.getKeyId())) {
				idx = entry.getKey();
			} else {
				prevIdx = entry.getKey();
			}
		}
		List<QuestionDto> questionsToUpdate = new ArrayList<QuestionDto>();
		QuestionDto qToMove = questionGroup.getQuestionMap().get(idx);
		QuestionDto targetQuestion = null;
		if (increment > 0) {
			targetQuestion = questionGroup.getQuestionMap().get(nextIdx);
		} else {
			targetQuestion = questionGroup.getQuestionMap().get(prevIdx);
		}
		Integer tempOrder = qToMove.getOrder();
		qToMove.setOrder(targetQuestion.getOrder());
		targetQuestion.setOrder(tempOrder);
		questionGroup.getQuestionMap().put(targetQuestion.getOrder(),
				targetQuestion);
		questionGroup.getQuestionMap().put(qToMove.getOrder(), qToMove);

		questionsToUpdate.add(qToMove);
		questionsToUpdate.add(targetQuestion);
		Label widgetToMove = null;
		Label targetWidget = null;
		for (Entry<Widget, Integer> entry : widgetRowMap.entrySet()) {
			if (entry.getKey() instanceof Label) {
				if (((Label) entry.getKey()).getText()
						.equals(qToMove.getText())) {
					widgetToMove = (Label) entry.getKey();
				} else if (((Label) entry.getKey()).getText().equals(
						targetQuestion.getText())) {
					targetWidget = (Label) entry.getKey();
				}
			}

			if (widgetToMove != null && targetWidget != null) {
				break;
			}
		}
		widgetToMove.setText(targetQuestion.getText());
		targetWidget.setText(qToMove.getText());

		surveyService.updateQuestionOrder(questionsToUpdate,
				new AsyncCallback<Void>() {

					@Override
					public void onFailure(Throwable caught) {
						setWorking(false);
						savingDialog.hide();
						MessageDialog errDia = new MessageDialog("Error",
								"Could not save ordering: "
										+ caught.getLocalizedMessage());
						errDia.showCentered();
					}

					@Override
					public void onSuccess(Void result) {
						setWorking(false);
						savingDialog.hide();
					}
				});

	}

	/**
	 * iterates over the questions in the current group to find the one that
	 * matches the id of the question passed in. If found, the map key is
	 * returned
	 * 
	 * @param question
	 * @return
	 */
	private Integer findKeyForQuestion(QuestionDto question) {
		Integer key = null;
		for (Entry<Integer, QuestionDto> entry : questionGroup.getQuestionMap()
				.entrySet()) {
			if (entry.getValue().getKeyId().equals(question.getKeyId())) {
				key = entry.getKey();
				break;
			}
		}
		return key;
	}

	private void deleteQuestion(QuestionDto question) {
		setWorking(true);
		Integer key = findKeyForQuestion(question);
		if (key != null) {
			questionGroup.getQuestionMap().remove(key);
			surveyService.deleteQuestion(question, question
					.getQuestionGroupId(), new AsyncCallback<String>() {

				@Override
				public void onFailure(Throwable caught) {
					setWorking(false);
					MessageDialog errDia = new MessageDialog("Error",
							"Could not delete question: "
									+ caught.getLocalizedMessage());
					errDia.showCentered();
				}

				@Override
				public void onSuccess(String result) {
					setWorking(false);
					loadData(questionGroup);
				}
			});
		}
	}

	@Override
	public Map<String, Object> getContextBundle(boolean doPopulation) {
		if (selectedQuestion != null && doPopulation) {
			bundle.put(BundleConstants.QUESTION_KEY, selectedQuestion);
		}
		return bundle;
	}
	
	@Override
	public void flushContext(){
		if(bundle!= null){
			bundle.remove(BundleConstants.QUESTION_KEY);
		}
	}
	

	@Override
	public void persistContext(CompletionListener listener) {
		if (listener != null) {
			listener.operationComplete(true, getContextBundle(true));
		}
	}
}