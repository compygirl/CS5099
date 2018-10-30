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

import java.util.ArrayList;
import savilerow.model.*;
import savilerow.CmdFlags;

public class TransformQuantifiedExpression extends TreeTransformerTopdown
{
    // For each ForallExpression, ExistsExpression or QuantifiedSum, rewrite into a And, Or, or WeightedSum.
    // For each ComprehensionMatrix, rewrite into a CompoundMatrix
    public TransformQuantifiedExpression(Model _m) {
        super(_m);
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Quantifier) {
	        
	        if(curnode.getChild(1) instanceof SimpleDomain) {
                ASTNode toexpand=curnode.getChild(2);
                ASTNode id=curnode.getChild(0);
                ASTNode dom=curnode.getChild(1);
                
                // Dom may contain a quantifier. Need to unroll that before asking for value set.
                TransformQuantifiedExpression tqe=new TransformQuantifiedExpression(m);
                dom=tqe.transform(dom);
                
                // Get bounds. If the domain is very large, do binary split.
                Intpair dombnds=dom.getBounds();
                
                ArrayList<ASTNode> expansion=new ArrayList<ASTNode>();
                
                if(dombnds.lower+20 >= dombnds.upper) {
                    // Small domain -- unroll it. 
                    ArrayList<Long> values=dom.getValueSet();
                    
                    for(int i=0; i<values.size(); i++) 
                    {
                        ASTNode unfoldedExpression=toexpand.copy();
                        
                        // Put in the condition.
                        if(! curnode.getChild(3).equals(new BooleanConstant(true))) {
                            if(curnode instanceof ForallExpression) 
                            {
                                unfoldedExpression=new Implies(curnode.getChild(3), unfoldedExpression);
                            }
                            else if(curnode instanceof ExistsExpression)
                            {
                                unfoldedExpression=new And(curnode.getChild(3), unfoldedExpression);
                            }
                            else
                            {   // sum
                                unfoldedExpression=new Times(curnode.getChild(3), unfoldedExpression);
                            }
                        }
                        
                        // Sub in the value. 
                        ReplaceASTNode t=new ReplaceASTNode(id, new NumberConstant(values.get(i)));
                        unfoldedExpression=t.transform(unfoldedExpression);
                        
                        expansion.add(unfoldedExpression);
                    }
                }
                else {
                    // Large domain -- binary split.
                    long mid=dombnds.lower+(dombnds.upper-dombnds.lower)/2;
                    
                    ASTNode intersect1=new IntegerDomain(new Range(new NumberConstant(dombnds.lower), new NumberConstant(mid)));
                    ASTNode intersect2=new IntegerDomain(new Range(new NumberConstant(mid+1), new NumberConstant(dombnds.upper)));
                    
                    // Make two copies and restrict the domains.
                    ASTNode copy1=curnode.copy();
                    ASTNode copy2=curnode.copy();
                    
                    copy1.setChild(1, new Intersect(intersect1, copy1.getChild(1)));
                    copy2.setChild(1, new Intersect(intersect2, copy2.getChild(1)));
                    
                    expansion.add(copy1); expansion.add(copy2);
                }
                
                ASTNode ex;
                // universal quantification 
                if(curnode instanceof ForallExpression) 
                {
                    ex = new And(expansion);
                }
                else if(curnode instanceof ExistsExpression)
                {
                    ex = new Or(expansion);
                }
                else
                {
                    assert curnode instanceof QuantifiedSum;
                    ex = new WeightedSum(expansion);  // Weighted sum with weights all 1.
                }
                
                ex.setParent(curnode.getParent());  // Do this so that Identifier.getCategory will work, getBoundsAST will work for bubble id's ... 
                
                // The simplifier changes the parent ptr again, to a NoTransformBox,
                // so the above line does not work.  
                
                TransformSimplify ts=new TransformSimplify();
                ex=ts.transform(ex);  // Specifically to throw away cases where the left side of
                // an implication evaluates to false.
                
                return new NodeReplacement(ex);
            }
            else {
                // Quantifier with matrix domain. 
                if(curnode.getChild(1).getCategory()>ASTNode.Constant) {
                    CmdFlags.println("ERROR: Domain of quantifier is not constant at unrolling time."+curnode.getChild(1));
                    assert false;
                    CmdFlags.exit();
                }
                assert curnode.getChild(1) instanceof MatrixDomain;
                
                ArrayList<ASTNode> indexdoms=curnode.getChild(1).getChildren();
                indexdoms.remove(0); indexdoms.remove(0); indexdoms.remove(0);
                
                ASTNode cm=TransformMatrixToAtoms.enumerateMatrixLiteral(indexdoms, curnode.getChild(0).toString(), m, curnode.getChild(1).getChild(0).isBooleanSet());
                //System.out.println(cm.toString());
                // Get a 1-d version
                TransformSimplify ts=new TransformSimplify();
                ASTNode varlistcm=ts.transform(new Flatten(cm.copy()));
                ArrayList<ASTNode> varlist=varlistcm.getChildren();
                varlist.remove(0);
                
                // Sub cm into the quantified expression
                ReplaceASTNode ra=new ReplaceASTNode(curnode.getChild(0), cm);
                ASTNode new_inner=ra.transform(curnode.getChild(2));
                
                for(int i=0; i<varlist.size(); i++) {
                    if(curnode instanceof ForallExpression) {
                        new_inner=new ForallExpression(varlist.get(i), curnode.getChild(1).getChild(0), new_inner);
                    }
                    else if(curnode instanceof ExistsExpression) {
                        new_inner=new ExistsExpression(varlist.get(i), curnode.getChild(1).getChild(0), new_inner);
                    }
                    else if(curnode instanceof QuantifiedSum) {
                        new_inner=new QuantifiedSum(varlist.get(i), curnode.getChild(1).getChild(0), new_inner);
                    }
                    else {
                        assert false;
                    }
                }
                
                // Get rid of introduced compound matrices if they are inside derefs/slices
                new_inner=ts.transform(new_inner);
                
                return new NodeReplacement(new_inner);
            }
	    }
	    else if(curnode instanceof ComprehensionMatrix) {
	        // Unlike other quantifiers above, unroll the whole thing because it
	        // needs to unroll into a one-dimensional CompoundMatrix, indexed from 1 by default. 
	        // Check for matrix domains in quantifiers.
	        for(int i=0; i<curnode.getChild(1).numChildren(); i++) {
	            ASTNode compquantifier=curnode.getChild(1).getChild(i);
	            if(compquantifier.getChild(1) instanceof MatrixDomain) {
	                // Replace with a set of SimpleDomains. 
	                ASTNode id=compquantifier.getChild(0);
	                
                    ArrayList<ASTNode> indexdoms=compquantifier.getChild(1).getChildren();
                    indexdoms.remove(0); indexdoms.remove(0); indexdoms.remove(0);
                    
                    ASTNode cm=TransformMatrixToAtoms.enumerateMatrixLiteral(indexdoms, compquantifier.getChild(0).toString(), m, compquantifier.getChild(1).getChild(0).isBooleanSet());
                    
                    TransformSimplify ts=new TransformSimplify();
                    ASTNode varlistcm=ts.transform(new Flatten(cm.copy()));
                    ArrayList<ASTNode> varlist=varlistcm.getChildren();
                    varlist.remove(0);
                    
                    // Sub cm into the comprehended expression expression
                    ReplaceASTNode ra=new ReplaceASTNode(compquantifier.getChild(0), cm);
                    ASTNode new_inner=ra.transform(curnode.getChild(0));
                    
                    // Sub cm into conditions.
                    ASTNode new_conditions=ra.transform(curnode.getChild(2));
                    
	                // Build a list of new quantifiers to go into the quantifier sequence using the base domain of the matrix. 
	                for(int j=0; j<varlist.size(); j++) {
	                    varlist.set(j, new ComprehensionForall(varlist.get(j), compquantifier.getChild(1).getChild(0)));
	                }
	                
	                ArrayList<ASTNode> newquantifiers=new ArrayList<ASTNode>();
	                for(int j=0; j<i; j++) newquantifiers.add(curnode.getChild(1).getChild(j));
	                newquantifiers.addAll(varlist);
	                for(int j=i+1; j<curnode.getChild(1).numChildren(); j++) newquantifiers.add(curnode.getChild(1).getChild(j));
	                
	                // Bail out here. If there are other MatrixDomains in the quantifier list, we will end up in this loop again. 
	                return new NodeReplacement(new ComprehensionMatrix(new_inner, newquantifiers, new_conditions, curnode.getChild(3)));
	            }
	        }
	        
	        
	        ArrayList<ASTNode> cm=comprehendComprehension(curnode, curnode.getChild(0), curnode.getChild(1), curnode.getChild(2), 0);
	        
            ASTNode idxdom=curnode.getChild(3);
            if(idxdom.isFiniteSet()) {
                /// Need to check somewhere else that this set is the right size!
                assert cm.size()==idxdom.getValueSet().size();
                if(cm.size()>0) {
                    return new NodeReplacement(new CompoundMatrix(idxdom, cm));
                }
                else {
                    //  Empty matrix, build a type for it. 
                    ArrayList<ASTNode> idxdoms=curnode.getChild(0).getIndexDomains();
                    if(idxdoms==null) {
                        //  Expression inside the comprehension is a scalar.
                        idxdoms=new ArrayList<ASTNode>();
                    }
                    idxdoms.add(0, idxdom);
                    ASTNode basedomain;
                    if(curnode.getChild(0).isRelation()) basedomain=new BooleanDomain(new EmptyRange());
                    else basedomain=new IntegerDomain(new EmptyRange());
                    return new NodeReplacement(new EmptyMatrix(new MatrixDomain(basedomain, idxdoms)));
                }
            }
            else {
                assert idxdom.isFiniteSetLower();
                // idxdom is bounded below, but may be holey. 
                assert idxdom.getCategory()==ASTNode.Constant;
                
                ArrayList<Intpair> intervals=idxdom.getIntervalSet();
                long len=cm.size();
                
                // find the upper bound.
                long ub=Long.MIN_VALUE;
                
                for(int i=0; i<intervals.size(); i++) {
                    if(intervals.get(i).upper==Long.MAX_VALUE) {
                        ub=intervals.get(i).lower+len-1;
                        break;
                    }
                    
                    long intervalsize=intervals.get(i).upper-intervals.get(i).lower+1;
                    if(len-intervalsize<=0) {
                        ub=intervals.get(i).lower+len-1;
                        break;
                    }
                    else {
                        len=len-intervalsize;
                    }
                }
                idxdom=new Intersect(new IntegerDomain(new Range(null, new NumberConstant(ub))), idxdom);
                
                if(cm.size()>0) {
                    return new NodeReplacement(new CompoundMatrix(idxdom, cm));
                }
                else {
                    //  Empty matrix, build a type for it. 
                    ArrayList<ASTNode> idxdoms=curnode.getChild(0).getIndexDomains();
                    if(idxdoms==null) {
                        //  Expression inside the comprehension is a scalar.
                        idxdoms=new ArrayList<ASTNode>();
                    }
                    idxdoms.add(0, idxdom);
                    ASTNode basedomain;
                    if(curnode.getChild(0).isRelation()) basedomain=new BooleanDomain(new EmptyRange());
                    else basedomain=new IntegerDomain(new EmptyRange());
                    return new NodeReplacement(new EmptyMatrix(new MatrixDomain(basedomain, idxdoms)));
                }
            }
	    }
	    return null;
	}
	
	ArrayList<ASTNode> comprehendComprehension(ASTNode originalexp, ASTNode innerexp, ASTNode quants, ASTNode condition, int quantidx) {
	    TransformSimplify ts=new TransformSimplify();
	    condition=ts.transform(condition);
	    
	    quants=ts.transform(quants);   // In case an outer comprehension or quantifier has not done constant evaluation. 
	    
	    if(condition.equals(new BooleanConstant(false))) {
	        return new ArrayList<ASTNode>();
	    }
	    
	    TransformQuantifiedExpression tqe=new TransformQuantifiedExpression(m);
        
	    if(quantidx<quants.numChildren()) {
	        ASTNode id=quants.getChild(quantidx).getChild(0);
	        
	        ASTNode comprehension_domain=quants.getChild(quantidx).getChild(1);
	        
	        // Unroll any quantifiers in the domain.
	        comprehension_domain=tqe.transform(comprehension_domain);
	        comprehension_domain=ts.transform(comprehension_domain);
            
	        ArrayList<Long> vals=comprehension_domain.getValueSet();
	        
	        // Accumulate stuff to go into CompoundMatrix
	        ArrayList<ASTNode> cm=new ArrayList<ASTNode>();
	        
	        for(int i=0; i<vals.size(); i++) {
	            long val=vals.get(i);
	            
	            ASTNode value;
	            if(quants.getChild(quantidx).getChild(1) instanceof BooleanDomain) {
	                value=new BooleanConstant( (val==0)?false:true );
	            }
	            else {
	                value=new NumberConstant(val);
	            }
	            
	            ReplaceASTNode r=new ReplaceASTNode(id, value);
	            
	            ASTNode iexp=r.transform(innerexp.copy());
	            ASTNode iquant=quants.copy();
	            for(int j=quantidx+1; j<quants.numChildren(); j++) {
	                // Do domains of rest of quantifiers. 
	                ASTNode dom=r.transform(iquant.getChild(j).getChild(1));
	                dom=ts.transform(dom);
	                iquant.getChild(j).setChild(1, dom);   
	            }
	            
	            //   r.transform(quants.copy());  // Why was this here? It appears to do nothing. 
	            
	            ASTNode icond=r.transform(condition.copy());
	            
	            // reconnect icond to the original comprehension, so that things like getDomainForId will work. 
	            icond.setParent(originalexp);
	            
	            cm.addAll(comprehendComprehension(originalexp, iexp, iquant, icond, quantidx+1));
	        }
	        return cm;
	    }
	    else {
	        // base case.
	        // Should be able to evaluate conditions with comprehensions in them .
	        TransformQuantifiedExpression tqe_inner=new TransformQuantifiedExpression(m);
	        condition=tqe_inner.transform(condition);
	        condition=ts.transform(condition);
	        
	        if(! (condition instanceof BooleanConstant)) System.out.println(condition); 
	        assert condition instanceof BooleanConstant;
	        if(condition.getValue()==1) {
	            ArrayList<ASTNode> retval=new ArrayList<ASTNode>();
	            retval.add(innerexp);
	            return retval;
	        }
	        else {
	            return new ArrayList<ASTNode>();
	        }
	    }
	}
}
