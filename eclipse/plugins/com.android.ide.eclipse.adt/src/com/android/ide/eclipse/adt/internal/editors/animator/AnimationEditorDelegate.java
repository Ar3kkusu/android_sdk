/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.animator;

import static com.android.ide.eclipse.adt.AdtConstants.EDITORS_NAMESPACE;

import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.eclipse.adt.internal.editors.XmlEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlCommonEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.resources.ResourceFolderType;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Editor for /res/animator XML files.
 */
@SuppressWarnings("restriction")
public class AnimationEditorDelegate extends XmlEditorDelegate {

    public static class Creator implements IXmlEditorCreator {
        @Override
        @SuppressWarnings("unchecked")
        public AnimationEditorDelegate createForFile(
                AndroidXmlCommonEditor delegator,
                IFileEditorInput input) {
            // get the IFile object and check it's the desired sub-resource folder
            IFile iFile = input.getFile();
            ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(iFile);
            ResourceFolderType type = resFolder == null ? null : resFolder.getType();
            if (ResourceFolderType.ANIM == type || ResourceFolderType.ANIMATOR == type) {
                return new AnimationEditorDelegate(delegator);
            }

            return null;
        }
    }

    /**
     * Old standalone-editor ID.
     * @deprecated Use {@link AndroidXmlCommonEditor#ID} instead.
     */
    @Deprecated
    public static final String OLD_STANDALONE_EDITOR_ID = EDITORS_NAMESPACE + ".animator.AnimationEditor"; //$NON-NLS-1$

    /** Root node of the UI element hierarchy */
    private UiElementNode mUiRootNode;
    /** The tag used at the root */
    private String mRootTag;

    private final AndroidXmlCommonEditor mDelegator;

    public AnimationEditorDelegate(AndroidXmlCommonEditor delegator) {
        mDelegator = delegator;
    }


    @Override
    public AndroidXmlCommonEditor getEditor() {
        return mDelegator;
    }

    @Override
    public void dispose() {
        // pass
    }

    @Override
    public UiElementNode getUiRootNode() {
        return mUiRootNode;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    @Override
    public void createFormPages() {
        /* Disabled for now; doesn't work quite right
        try {
            addPage(new AnimatorTreePage(this));
        } catch (PartInitException e) {
            AdtPlugin.log(IStatus.ERROR, "Error creating nested page"); //$NON-NLS-1$
            AdtPlugin.getDefault().getLog().log(e.getStatus());
        }
        */
     }

    /* (non-java doc)
     * Change the tab/title name to include the project name.
     */
    @Override
    public void setInput(IEditorInput input) {
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput) input;
            IFile file = fileInput.getFile();
            mDelegator.setPartName(String.format("%1$s", file.getName()));
        }
    }

    /**
     * Processes the new XML Model.
     *
     * @param xmlDoc The XML document, if available, or null if none exists.
     */
    @Override
    public void xmlModelChanged(Document xmlDoc) {
        Element rootElement = xmlDoc.getDocumentElement();
        if (rootElement != null) {
            mRootTag = rootElement.getTagName();
        }

        // create the ui root node on demand.
        initUiRootNode(false /*force*/);

        if (mRootTag != null
                && !mRootTag.equals(mUiRootNode.getDescriptor().getXmlLocalName())) {
            AndroidTargetData data = getEditor().getTargetData();
            if (data != null) {
                ElementDescriptor descriptor;
                if (getFolderType() == ResourceFolderType.ANIM) {
                    descriptor = data.getAnimDescriptors().getElementDescriptor(mRootTag);
                } else {
                    descriptor = data.getAnimatorDescriptors().getElementDescriptor(mRootTag);
                }
                // Replace top level node now that we know the actual type

                // Disconnect from old
                mUiRootNode.setEditor(null);
                mUiRootNode.setXmlDocument(null);

                // Create new
                mUiRootNode = descriptor.createUiNode();
                mUiRootNode.setXmlDocument(xmlDoc);
                mUiRootNode.setEditor(mDelegator);
            }
        }

        if (mUiRootNode.getDescriptor() instanceof DocumentDescriptor) {
            mUiRootNode.loadFromXmlNode(xmlDoc);
        } else {
            mUiRootNode.loadFromXmlNode(rootElement);
        }
    }

    @Override
    public void initUiRootNode(boolean force) {
        // The manifest UI node is always created, even if there's no corresponding XML node.
        if (mUiRootNode == null || force) {
            ElementDescriptor descriptor;
            boolean reload = false;
            AndroidTargetData data = getEditor().getTargetData();
            if (data == null) {
                descriptor = new DocumentDescriptor("temp", null /*children*/);
            } else {
                if (getFolderType() == ResourceFolderType.ANIM) {
                    descriptor = data.getAnimDescriptors().getElementDescriptor(mRootTag);
                } else {
                    descriptor = data.getAnimatorDescriptors().getElementDescriptor(mRootTag);
                }
                reload = true;
            }
            mUiRootNode = descriptor.createUiNode();
            mUiRootNode.setEditor(mDelegator);

            if (reload) {
                onDescriptorsChanged();
            }
        }
    }

    private ResourceFolderType getFolderType() {
        IFile inputFile = mDelegator.getInputFile();
        if (inputFile != null) {
            String folderName = inputFile.getParent().getName();
            return  ResourceFolderType.getFolderType(folderName);
        }
        return ResourceFolderType.ANIMATOR;
    }

    private void onDescriptorsChanged() {
        IStructuredModel model = mDelegator.getModelForRead();
        if (model != null) {
            try {
                Node node = mDelegator.getXmlDocument(model).getDocumentElement();
                mUiRootNode.reloadFromXmlNode(node);
            } finally {
                model.releaseFromRead();
            }
        }
    }
}