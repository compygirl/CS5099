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
import savilerow.*;

import java.util.*;

public class TransformToFlatClass extends TreeTransformerBottomUp
{
    CSEContainerClass cse;
    
    public TransformToFlatClass(Model mod)
    {
        super(mod);
        cse=new CSEContainerClassIdentical(m);
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    // If the node needs flattening and contains decision variables, and
	    // is not already flattened or top-level..
	    if(curnode.toFlatten(false) && curnode.getCategory()>ASTNode.Quantifier
	        && !(curnode.getParent()==null) && !(curnode.getParent() instanceof ToVariable))
	    {
	        // What if the reason curnode is non-flat is that itis in an expression
	        // where other things are parameters or quantifier veriables?
	        // i.e. it will be flat when quantifiers are unrolled.
	        // NEED some extra tests here to avoid this.
	        // OR quantifiers with conditions in them to avoid this problem
	        // i.e. problem occurs mainly with forall - implies pattern, and exists-and pattern
	        
	        // Don't flatten when curnode is the rhs of an implies, inside a forall, and the lhs
	        // is a quantifier expression.
	        if(curnode.getParent() instanceof Implies && curnode.getParent().getParent() instanceof ForallExpression
	            && curnode.getParent().getChild(0).getCategory()<=ASTNode.Quantifier) {
	            return null;
	        }
	        
	        // Don't flatten when curnode is in an AND, inside an exists, and everything else
	        // in the AND is quantifier expressions.
	        /// Actually shouldn't need to worry about this because contents of And don't need to be
	        // flattened anyway. 
	        if(curnode.getParent() instanceof And && curnode.getParent().getParent() instanceof ExistsExpression) {
	            ArrayList<ASTNode> ch=curnode.getParent().getChildren();
	            boolean allQuantExp=true;
	            for(ASTNode c : ch) {
	                if( (! c.equals(curnode)) && c.getCategory()>ASTNode.Quantifier) {
	                    allQuantExp=false; break;
	                }
	            }
	            if(allQuantExp) return null; 
	        }
	        
	        System.out.println("Flattening: "+curnode);
	        
	        // Collect all quantifier id's in the exression, and their corresponding quantifier.
	        ArrayList<ASTNode> q_id=new ArrayList<ASTNode>();         // Collect quantifier id's in curnode
	        ArrayList<ASTNode> quantifiers=new ArrayList<ASTNode>();  // collect quantifiers.
	        ArrayList<ASTNode> conditions=new ArrayList<ASTNode>();   // Collect parameter expressions
	        
	        findQuantifierIds(q_id, quantifiers, conditions, curnode);
	        
	        // Extract and process domains from the quantifiers
	        ArrayList<ASTNode> qdoms=new ArrayList<ASTNode>();
	        extractDomains(q_id, quantifiers, conditions, qdoms, curnode);
	        
	        // Save the list in the order they appear in the expression.
	        ArrayList<ASTNode> q_id_ordered=new ArrayList<ASTNode>(q_id);
	        ArrayList<ASTNode> qdoms_ordered=new ArrayList<ASTNode>(qdoms);
	        
	        sortQuantifiers(q_id, quantifiers, qdoms);
	        
	        // CSE
	        ASTNode temp=cse.lookupCSE(curnode, q_id_ordered, qdoms_ordered, conditions);
	        
	        if(temp != null && CmdFlags.getUseCSE()) {
	            return new NodeReplacement(temp);
	        }
	        
	        ASTNode auxvar;
	        // Get bounds to obtain domain for auxiliary variable matrix.
	        PairASTNode bnds=curnode.getBoundsAST();
	        
	        if(q_id.size()>0) {
	            ASTNode auxvarmatrixid=m.global_symbols.newAuxiliaryVariableMatrix(bnds.e1, bnds.e2, q_id, qdoms, conditions);  // This internally special cases for 0/1 vars now.
	            auxvar=new MatrixDeref(auxvarmatrixid, q_id);
	            m.global_symbols.auxVarRepresentsConstraint( auxvarmatrixid.toString(), curnode.toString());
	        }
	        else {
	            // It's just a single aux variable.
	            auxvar=m.global_symbols.newAuxiliaryVariable(bnds.e1, bnds.e2);
	            m.global_symbols.auxVarRepresentsConstraint( auxvar.toString(), curnode.toString());
	        }
	        
	        ASTNode flatcon=new ToVariable(curnode, auxvar.copy());
	        // Wrap flatcon in the conditions.
	        if(conditions.size()>0) {
	            flatcon=new Implies(new And(conditions), flatcon);
	        }
	        
	        // Wrap flatcon in the appropriate forall quantifiers.
	        for(int i=q_id.size()-1; i>=0; i--) {
	            flatcon=new ForallExpression(q_id.get(i), qdoms.get(i), flatcon);
	        }
	        
	        cse.storePotentialCSE(curnode, q_id_ordered, qdoms_ordered, conditions, auxvar.copy());
	        
	        TransformSimplify ts=new TransformSimplify();
	        flatcon=ts.transform(flatcon);
            
	        System.out.println("Flattened to: "+auxvar+" with extra constraints: "+flatcon);
            System.out.println("with matrix domain: ");
            if(m.global_symbols.getDomain(auxvar.toString())!=null) System.out.println(m.global_symbols.getDomain(auxvar.toString()));
            else System.out.println(m.global_symbols.getDomain(auxvar.getChild(0).toString()));
            
            return new NodeReplacement(auxvar, null, flatcon);
	    }
	    return null;
	}
	
	public static void findQuantifierIds(ArrayList<ASTNode> q_id, ArrayList<ASTNode> quantifiers, ArrayList<ASTNode> conditions, ASTNode curnode) {
	    ArrayDeque<ASTNode> list=new ArrayDeque<ASTNode>();
        list.add(curnode);
        
        expressionloop:
        while(list.size()>0) {
            ASTNode a=list.removeFirst();
            list.addAll(a.getChildren());
            
            if(a instanceof Identifier && a.getCategory()==ASTNode.Quantifier) {
                // If a is already in q_id, don't process it again.
                if(q_id.indexOf(a)>-1) {
                    continue expressionloop;
                }
                
                // Get the quantifier for a
                // Go up from curnode.getParent not a. Don't want to add id's from quantifiers inside curnode or
                // a quantifier equal to curnode
                ASTNode a_quantifier=a.getQuantifier(curnode.getParent());
                if(a_quantifier==null) {
                    // This means a is a quantifier ID for a quantifier inside curnode. So we don't want to store a.
                    continue expressionloop;
                }
                
                q_id.add(a);
                quantifiers.add(a_quantifier);
                
                // Collect conditions on Foralls and Exists quantifiers.
                if(a_quantifier instanceof ForallExpression 
                    && a_quantifier.getChild(2) instanceof Implies
                    && a_quantifier.getChild(2).getChild(0).getCategory()<=ASTNode.Quantifier) {
                    // There's a condition on the forall quantifier.
                    ASTNode cond=a_quantifier.getChild(2).getChild(0);
                    if(cond instanceof And) conditions.addAll(cond.getChildren());
                    else conditions.add(cond);
                }
                if(a_quantifier instanceof ExistsExpression
                    && a_quantifier.getChild(2) instanceof And) {
                    ArrayList<ASTNode> conjuncts=a_quantifier.getChild(2).getChildren();
                    for(ASTNode conjunct : conjuncts) {
                        if(conjunct.getCategory() <= ASTNode.Quantifier) {
                            conditions.add(conjunct);  // No need to check if it's an And, because nested Ands should
                            // have been mashed together by simplify.
                        }
                    }
                }
            }
        }
        
        // Filter conditions for q-identifiers not in the 'q_id' arraylist
        for(int i=0; i<conditions.size(); i++) {
            ASTNode c=conditions.get(i);
            ArrayList<ASTNode> problem_ids=new ArrayList<ASTNode>();
            
            list.clear();
            list.add(c);
            while(list.size()>0) {
                ASTNode a=list.removeFirst();
                list.addAll(a.getChildren());
                if(a instanceof Identifier && (a.getCategory()==ASTNode.Quantifier || a.getCategory()==ASTNode.Undeclared) 
                    && q_id.indexOf(a)==-1 && a.getQuantifier(curnode.getParent())!=null) {         // a is 
                    problem_ids.add(a);
                }
            }
            
            if(problem_ids.size()>0) {
                System.out.println("DELETING CONDITION "+conditions.get(i));
                conditions.remove(i);    /// Very basic now -- just removes the condition.
                i--;                     /// Later can do some manipulation -- e.g. forall i,j : int(1..n). i<j => ...m[j]  j can only be 1..n-1.
            }
        }
        
        // Also need to look up the tree for nots -- this will really mess things up. !!
        
	}
	
	public static void extractDomains(ArrayList<ASTNode> q_id, ArrayList<ASTNode> quantifiers, ArrayList<ASTNode> conditions, ArrayList<ASTNode> qdoms, ASTNode curnode) {
	    ArrayDeque<ASTNode> list=new ArrayDeque<ASTNode>();
        
	    for(int i=0; i<quantifiers.size(); i++) {
	        ASTNode qdomlist=quantifiers.get(i).getChild(1);
            
            ASTNode qdom=qdomlist.getChild(quantifiers.get(i).getChild(0).getChildren().indexOf(q_id.get(i)));
            
            // Find q-id's not in q_id.
            list.clear();
            list.add(qdom);
            ArrayList<ASTNode> problem_ids=new ArrayList<ASTNode>();
            while(list.size()>0) {
                ASTNode a=list.removeFirst();
                list.addAll(a.getChildren());
                if(a instanceof Identifier && a.getCategory()==ASTNode.Quantifier 
                    && q_id.indexOf(a)==-1 && a.getQuantifier(curnode)!=null) {
                    problem_ids.add(a);
                }
            }
            
            if(problem_ids.size()>0) {
                System.out.println("Problem ids:"+problem_ids);
                // Sub in bounds for each item in problem_ids.
                qdom=qdom.copy();
                for(ASTNode getridid : problem_ids) {
                    PairASTNode bds=getridid.getBoundsAST();
                    ASTNode replacement=new Interval(bds.e1,bds.e2);
                    TreeTransformer t=new ReplaceASTNode(getridid, replacement);
                    qdom=t.transform(qdom);
                }
                
                // Now call getBoundsAST for the whole thing and make a new domain from that.
                // PROBLEM HERE: this will eliminate quantifier id's.
                qdom=new IntegerDomain(new Range(qdom.getBoundsAST().e1, qdom.getBoundsAST().e2));
            }
            
            // If the lower bound contains quantifier id's, take it out and replace it with a condition.
            // FRAGILE!
            if(qdom.getChild(0).getChild(0).getChild(0).getCategory()==ASTNode.Quantifier) {
                // Make a new condition to enforce the lower bound.
                ASTNode newcondition=new LessEqual(qdom.getChild(0).getChild(0).getChild(0), q_id.get(i));
                conditions.add(newcondition);
                
                // Call getBoundsAST (again) to replace the quantifier ID with its domain from the quantifier.
                qdom.getChild(0).getChild(0).setChild(0, qdom.getBoundsAST().e1);
            }
            
            qdoms.add(qdom);
        }
    }
	
    public static void sortQuantifiers(ArrayList<ASTNode> q_id, ArrayList<ASTNode> quantifiers, ArrayList<ASTNode> qdoms) {
        // quantifiers must be ordered for output in the same order as they are inthe input.
        // because domains of inner quantifiers can contain symbols from outer ones.
        // Co-sort q_id and quantifiers and qdoms
        for(int i=1; i<quantifiers.size(); i++) {
            // insertion sort -- swap i into position.
            for(int j=i; j>0; j--) {
                if(quantifiers.get(j).isParentTransitive(quantifiers.get(j-1))) {
                    // swap q_id, qdom and quantifier
                    ASTNode quantemp=quantifiers.get(j);
                    quantifiers.set(j, quantifiers.get(j-1));
                    quantifiers.set(j-1, quantemp);
                    
                    ASTNode qidtemp=q_id.get(j);
                    q_id.set(j, q_id.get(j-1));
                    q_id.set(j-1, qidtemp);
                    
                    ASTNode qdomtemp=qdoms.get(j);
                    qdoms.set(j, qdoms.get(j-1));
                    qdoms.set(j-1, qdomtemp);
                }
                else {
                    break; // the inner loop.
                }
            }
        }
    }
	
}
