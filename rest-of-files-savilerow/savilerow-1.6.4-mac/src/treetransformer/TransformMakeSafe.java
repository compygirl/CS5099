package savilerow.treetransformer;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/


import savilerow.expression.*;
import savilerow.model.*;

import java.util.ArrayList;
import java.util.HashMap;

//  Add 'definedness constraints' to any expression that might be undef.
//  This must be the first transformation of any expression. 

/* *****************************************************************************

Cases covered

Boolean matrix: x[j]=i  becomes  ( x[j] /\ j \in {range} )=i 
Non-boolean matrix:  x[j]=i  becomes   x[j]=i /\ j \in {range} 

Division:   x/y=5 becomes x/y=5 /\ y!=0


Existential quantifier:
domain -- bob constraint is ANDed to the entire quantifier.
exp -- bob constraint is ANDed within the quantifier


Universal quantifier:
domain -- bob constraint is ANDed to the entire quantifier
exp -- bob constraint is ANDed within the quantifier

***************************************************************************** */

public class TransformMakeSafe extends TreeTransformerBottomUp
{
    public TransformMakeSafe(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    ASTNode bob;
	    ASTNode replace_current_node;
	    if(curnode instanceof Divide && !(curnode.getParent() instanceof Tag)) {
	        Intpair bnds=curnode.getChild(1).getBounds();
	        if(bnds.lower<=0 && bnds.upper>=0) {
	            // If denominator can be 0.
	            // The BOB for a divide is just that the denominator is not 0.
	            bob=new AllDifferent(curnode.getChild(1), new NumberConstant(0));
	            
	            // Replace the division with a SafeDivide with a default value of 0.
	            // SafeDivide is a total function.
	            // May reduce propagation when 0 would not normally be in the domain of 
	            replace_current_node=new SafeDivide(curnode.getChild(0), curnode.getChild(1));
            }
            else {
                bob=null;
                replace_current_node=new Tag(curnode,true);
            }
        }
        else if(curnode instanceof Mod && !(curnode.getParent() instanceof Tag)) {
            Intpair bnds=curnode.getChild(1).getBounds();
	        if(bnds.lower<=0 && bnds.upper>=0) {
	            // If denominator can be 0.
	            // The BOB for a mod is just that the denominator is not 0.
	            bob=new AllDifferent(curnode.getChild(1), new NumberConstant(0));
	            
	            // Replace the mod with a SafeMod with a default value of 0.
	            // SafeMod is a total function.
	            // May reduce propagation when 0 would not normally be in the domain of 
	            replace_current_node=new SafeMod(curnode.getChild(0), curnode.getChild(1));
            }
            else {
                bob=null;
                replace_current_node=new Tag(curnode,true);
            }
        }
        else if(curnode instanceof Power && !(curnode.getParent() instanceof Tag)) {
            Intpair bnds0=curnode.getChild(0).getBounds();
            Intpair bnds1=curnode.getChild(1).getBounds();
	        if((bnds0.lower<=0 && bnds0.upper>=0 && bnds1.lower<=0 && bnds1.upper>=0) || bnds1.lower<0) {
	            // Power is undefined for 0**0 and getChild(1)<0
	            bob=new And(new Or(
	                new AllDifferent(curnode.getChild(0), new NumberConstant(0)),
	                new AllDifferent(curnode.getChild(1), new NumberConstant(0))),
	                new LessEqual(new NumberConstant(0), curnode.getChild(1)));
	            
	            // Replace the pow with a SafePower with a default value.
	            // SafePower is a total function.
	            replace_current_node=new SafePower(curnode.getChild(0), curnode.getChild(1));
            }
            else {
                bob=null;
                replace_current_node=new Tag(curnode,true);
            }
        }
        else if(curnode instanceof Factorial && !(curnode.getParent() instanceof Tag)) {
            Intpair bnds0=curnode.getChild(0).getBounds();
            
            if(bnds0.lower<0) {
	            // Factorial is undefined for negative numbers.
	            bob=new LessEqual(new NumberConstant(0), curnode.getChild(0));
	            
	            // Replace the factorial with a SafeFactorial with a default value of 0.
	            replace_current_node=new SafeFactorial(curnode.getChild(0));
            }
            else {
                bob=null;
                replace_current_node=new Tag(curnode,true);
            }
        }
        else if( (curnode instanceof MinVector || curnode instanceof MaxVector) &&  !(curnode.getParent() instanceof Tag)) {
            // Undefined when the vector is empty.
            // length(matrix)>0 
            bob=new Less(new NumberConstant(0), new Length(curnode.getChild(0)));
            replace_current_node=new Tag(curnode, true);
        }
        else if( (curnode instanceof MatrixDeref || curnode instanceof MatrixSlice) && !(curnode.getParent() instanceof Tag)) {
            ArrayList<ASTNode> conditions=new ArrayList<ASTNode>();
            
            ASTNode matrix=curnode.getChild(0);
            ArrayList<ASTNode> indices = curnode.getChildren(); 
            indices.remove(0);
            assert matrix.getDimension()>0;
            
            // These types can contain a MatrixSlice (as the first child), therefore they can contain
            // a Tag(MatrixSlice(...)) so must not use any methods that Tag does not have. 
            
            ASTNode qvar=new Identifier(m.global_symbols.newAuxId(), m.global_symbols);
            
            ArrayList<ASTNode> deref_arg=new ArrayList<ASTNode>(); deref_arg.add(qvar);
            
            ASTNode indices_cm=CompoundMatrix.makeCompoundMatrix(indices);
            
            // Find the items in indices that are _not_  InfiniteIntegerDomain
            ArrayList<Intpair> finite_items=new ArrayList<Intpair>();
            for(int i=0; i<indices.size(); i++) {
                if(!(indices.get(i).isSet())) {
                    finite_items.add(new Intpair(i+1, i+1));
                }
            }
            
            ASTNode constraint=new InSet(new SafeMatrixDeref(indices_cm, deref_arg), 
                new SafeMatrixDeref(new Indices(matrix), deref_arg));
            
            bob=new ForallExpression(qvar, Intpair.makeDomain(finite_items, false), constraint);
            
            // Replace with function with default value. 
            if(curnode instanceof MatrixDeref) {
                replace_current_node=new SafeMatrixDeref(curnode.getChild(0), indices);
            }
            else {
                replace_current_node=new Tag(new MatrixSlice(curnode.getChild(0), indices, m.global_symbols), true);
            }
        }
        else {
            return null;
        }
        
        // Now add any necessary quantifiers to bob.
        if(bob!=null) {
            ASTNode parnode=curnode;
            ASTNode previous=null;   // the child of parnode. 
            ASTNode previous2=null;  //  the child of previous
            
            while( parnode!=null  &&  !(parnode.isRelation()) ) {
                
                // Forall and Exists: if the partial function is in the quantified expression, the bob 
                // constraint remains in there because it is relational. 
                // If the partial function is in the domain, it should not be wrapped. So we should do
                // nothing for Forall and Exists. 
                
                // This leaves QuantifiedSum and ComprehensionMatrix 
                
                if(parnode instanceof QuantifiedSum) {
                    // If the bob came from the domain, do nothing. Otherwise wrap it. 
                    if(! previous.equals(parnode.getChild(1)) ) {
                        // First add the condition. Should disappear when simplified if 
                        // there is no condition. 
                        if(parnode.numChildren()>3) {
                            bob=new Implies(parnode.getChild(3), bob);
                        }
                        
                        bob=new ForallExpression(parnode.getChild(0), parnode.getChild(1), bob);
                    }
                }
                else if(parnode instanceof ComprehensionMatrix) {
                    // Partial function could be in a comprehension domain, in
                    // the comprehended expression, or in the index domain. 
                    // (not the condition because it would not bubble out of there)
                    ArrayList<ASTNode> qlist=parnode.getChild(1).getChildren();
                    
                    if(previous.equals(parnode.getChild(0)) ) {
                        // Partial function is in the quantified expression. 
                        // First add the condition. Should disappear when simplified if 
                        // there is no condition. 
                        bob=new Implies(parnode.getChild(2), bob);
                        
                        // Now add each of the quantifiers in the comprehension.
                        for(int i=qlist.size()-1; i>=0; i--) {
                            // Pull apart each quantifier in the comprehension and
                            // wrap bob with the equivalent Forall quantifier. 
                            
                            bob=new ForallExpression(qlist.get(i).getChild(0), qlist.get(i).getChild(1), bob);
                        }
                    }
                    else if( previous.equals(parnode.getChild(1))){
                        // Partial function is in one of the quantifier domains. 
                        // Find out which one and wrap with all the outer ones. 
                        
                        int qno=-1;
                        
                        for(int i=qlist.size()-1; i>=0; i--) {
                            // Pull apart each quantifier in the comprehension 
                            if(qlist.get(i).equals(previous2)) {
                                qno=i;
                                break;
                            }
                        }
                        
                        assert qno!=-1;
                        
                        for(int i=qno-1; i>=0; i--) {
                            // Pull apart each quantifier in the comprehension 
                            // wrap bob with the equivalent Forall quantifier. 
                            
                            bob=new ForallExpression(qlist.get(i).getChild(0), qlist.get(i).getChild(1), bob);
                        }
                        
                    }
                    // else the partial function is in the index domain. No need to
                    // wrap bob; the index domain should not include any comprehension id's.
                }
                previous2=previous;
                previous=parnode;
                parnode=parnode.getParent();
            }
            return new NodeReplacement(replace_current_node, bob, null);
        }
        return new NodeReplacement(replace_current_node);  // In case there is no bob.
    }
    
}

