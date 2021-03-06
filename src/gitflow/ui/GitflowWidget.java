/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gitflow.ui;

import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.Consumer;
import com.intellij.openapi.actionSystem.ActionGroup;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import gitflow.GitflowActions;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.ui.branch.GitBranchWidget;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Status bar widget which displays actions for git flow
 * @author Kirill Likhodedov, Opher Vishnia
 */
public class GitflowWidget extends EditorBasedWidget implements StatusBarWidget.MultipleTextValuesPresentation,
        StatusBarWidget.Multiframe,
        GitRepositoryChangeListener {
    private volatile String myText = "";
    private volatile String myTooltip = "";
    private final String myMaxString;

    private GitflowActions actions;

    public GitflowWidget(Project project) {
        super(project);
        project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
        myMaxString = "Git: Rebasing master";
    }

    @Override
    public StatusBarWidget copy() {
        return new GitBranchWidget(getProject());
    }

    @NotNull
    @Override
    public String ID() {
        return GitflowWidget.class.getName();
    }

    @Override
    public WidgetPresentation getPresentation(@NotNull PlatformType type) {
        return this;
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent event) {
        update();
    }

    @Override
    public void fileOpened(FileEditorManager source, VirtualFile file) {
        update();
    }

    @Override
    public void fileClosed(FileEditorManager source, VirtualFile file) {
        update();
    }

    @Override
    public void repositoryChanged(@NotNull GitRepository repository) {
        update();
    }

    @Override
    public ListPopup getPopupStep() {
        Project project = getProject();
        if (project == null) {
            return null;
        }
        GitRepository repo = GitBranchUtil.getCurrentRepository(project);
        if (repo == null) {
            return null;
        }

        ActionGroup popupGroup = actions.getActions();
        ListPopup listPopup = new PopupFactoryImpl.ActionGroupPopup("Gitflow Actions", popupGroup, SimpleDataContext.getProjectContext(project), false, false, false, true, null, -1,
                null, null);

        return listPopup;
    }

    @Override
    public String getSelectedValue() {
        return myText;
    }

    @NotNull
    @Override
    public String getMaxValue() {
        return myMaxString;
    }

    @Override
    public String getTooltipText() {
        return myTooltip;
    }

    @Override
    // Updates branch information on click
    public Consumer<MouseEvent> getClickConsumer() {
        return new Consumer<MouseEvent>() {
            public void consume(MouseEvent mouseEvent) {
                update();
            }
        };
    }

    private void update() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                Project project = getProject();
                if (project == null) {
                    emptyTextAndTooltip();
                    return;
                }

                GitRepository repo = GitBranchUtil.getCurrentRepository(project);
                if (repo == null) { // the file is not under version control => display nothing
                    emptyTextAndTooltip();
                    return;
                }

                int maxLength = myMaxString.length() - 1; // -1, because there are arrows indicating that it is a popup

                actions = new GitflowActions(project);

                boolean hasGitflow = actions.hasGitflow();

                myText = hasGitflow ? "Gitflow": "No Gitflow";
                myTooltip = getDisplayableBranchTooltip(repo);
                myStatusBar.updateWidget(ID());
            }
        });
    }

    private void emptyTextAndTooltip() {
        myText = "";
        myTooltip = "";
    }

    @NotNull
    private static String getDisplayableBranchTooltip(GitRepository repo) {
        String text = GitBranchUtil.getDisplayableBranchText(repo);
        if (!GitUtil.justOneGitRepository(repo.getProject())) {
            return text + "\n" + "Root: " + repo.getRoot().getName();
        }
        return text;
    }
}
