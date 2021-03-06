/*
 * Copyright 2013 Julien Dramaix.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gwt.resources.gss;

import com.google.common.base.Objects;
import com.google.common.css.compiler.ast.CssCompilerPass;
import com.google.common.css.compiler.ast.CssCompositeValueNode;
import com.google.common.css.compiler.ast.CssDeclarationNode;
import com.google.common.css.compiler.ast.CssFunctionNode;
import com.google.common.css.compiler.ast.CssNode;
import com.google.common.css.compiler.ast.CssNumericNode;
import com.google.common.css.compiler.ast.CssPropertyNode;
import com.google.common.css.compiler.ast.CssPropertyValueNode;
import com.google.common.css.compiler.ast.CssTreeVisitor;
import com.google.common.css.compiler.ast.CssValueNode;
import com.google.common.css.compiler.ast.DefaultTreeVisitor;
import com.google.common.css.compiler.ast.MutatingVisitController;
import com.google.common.css.compiler.passes.BiDiFlipper;

import java.util.List;

public class RecordingBidiFlipper extends DefaultTreeVisitor implements CssCompilerPass {

  /**
   * This {@link MutatingVisitController} will record if an effective mutation is done
   */
  private static class RecordingMutatingVisitController implements MutatingVisitController {
    private MutatingVisitController delegate;
    private CssDeclarationNode visitingDeclarationNode;
    private boolean hasMutation;

    private RecordingMutatingVisitController(MutatingVisitController delegate) {
      this.delegate = delegate;
    }

    @Override
    public void removeCurrentNode() {
      delegate.removeCurrentNode();
    }

    @Override
    public <T extends CssNode> void replaceCurrentBlockChildWith(List<T> replacementNodes,
        boolean visitTheReplacementNodes) {
      // In our case, the list of replacement node should contain only one CssDeclarationNode
      if (!hasMutation && visitingDeclarationNode != null && replacementNodes.size() == 1 &&
          replacementNodes.get(0) instanceof CssDeclarationNode) {
        CssDeclarationNode newDeclarationNode = (CssDeclarationNode) replacementNodes.get(0);
        hasMutation |= isNotEqual(visitingDeclarationNode, newDeclarationNode);
      }

      delegate.replaceCurrentBlockChildWith(replacementNodes, visitTheReplacementNodes);
    }

    public void setVisitingDeclarationNode(CssDeclarationNode visitingDeclarationNode) {
      this.visitingDeclarationNode = visitingDeclarationNode;
    }

    @Override
    public void startVisit(CssTreeVisitor visitor) {
      delegate.startVisit(visitor);
    }

    @Override
    public void stopVisit() {
      delegate.stopVisit();
    }

    private boolean compositeValueEqual(CssCompositeValueNode first, CssCompositeValueNode second) {
      return valueNodeListEqual(first.getValues(), second.getValues());
    }

    private boolean functionNodeEqual(CssFunctionNode first, CssFunctionNode second) {
      // TODO : this test is maybe not needed, the BidiFlipper doesn't change the function name
      if (!Objects.equal(first.getFunctionName(), second.getFunctionName())) {
        return false;
      }
      return valueNodeListEqual(first.getArguments().getChildren(), second.getArguments()
          .getChildren());
    }

    private boolean isNotEqual(CssDeclarationNode first, CssDeclarationNode second) {
      return !propertyNameEqual(first.getPropertyName(), second.getPropertyName()) ||
          !propertyValuesEqual(first.getPropertyValue(), second.getPropertyValue());
    }

    private boolean numericNodeEqual(CssNumericNode first, CssNumericNode second) {
      return Objects.equal(first.getNumericPart(), second.getNumericPart()) &&
          Objects.equal(first.getUnit(), second.getUnit());
    }

    private boolean propertyNameEqual(CssPropertyNode first, CssPropertyNode second) {
      return Objects.equal(first.getPropertyName(), second.getPropertyName());
    }

    private boolean propertyValuesEqual(CssPropertyValueNode first, CssPropertyValueNode second) {
      return valueNodeListEqual(first.getChildren(), second.getChildren());
    }

    private boolean valueEqual(CssValueNode first, CssValueNode second) {
      if (first.getClass() != second.getClass()) {
        return false;
      }

      if (first instanceof CssCompositeValueNode) {
        return compositeValueEqual((CssCompositeValueNode) first, (CssCompositeValueNode) second);
      } else if (first instanceof CssFunctionNode) {
        return functionNodeEqual((CssFunctionNode) first, (CssFunctionNode) second);
      } else if (first instanceof CssNumericNode) {
        return numericNodeEqual((CssNumericNode) first, (CssNumericNode) second);
      } else {
        return Objects.equal(first.getValue(), second.getValue());
      }
    }

    private boolean valueNodeListEqual(List<CssValueNode> firstValues,
        List<CssValueNode> secondValues) {
      if (firstValues.size() != secondValues.size()) {
        return false;
      }

      for (int i = 0; i < firstValues.size(); i++) {
        CssValueNode firstNode = firstValues.get(i);
        CssValueNode secondNode = secondValues.get(i);

        if (!valueEqual(firstNode, secondNode)) {
          return false;
        }
      }

      return true;
    }
  }

  private static BiDiFlipper delegate;
  private RecordingMutatingVisitController mutatingVisitController;

  public RecordingBidiFlipper(MutatingVisitController visitController, boolean swapLtrRtlInUrl,
      boolean swapLeftRightInUrl, boolean shouldFlipConstantReferences) {

    this.mutatingVisitController = new RecordingMutatingVisitController(visitController);
    this.delegate = new BiDiFlipper(mutatingVisitController, swapLtrRtlInUrl, swapLeftRightInUrl,
        shouldFlipConstantReferences);
  }

  @Override
  public boolean enterDeclaration(CssDeclarationNode declaration) {
    mutatingVisitController.setVisitingDeclarationNode(declaration);
    return delegate.enterDeclaration(declaration);
  }

  /**
   * return true if at least one node was flipped, false otherwise.
   */
  public boolean nodeFlipped() {
    return mutatingVisitController.hasMutation;
  }

  @Override
  public void runPass() {
    mutatingVisitController.startVisit(this);
  }
}
