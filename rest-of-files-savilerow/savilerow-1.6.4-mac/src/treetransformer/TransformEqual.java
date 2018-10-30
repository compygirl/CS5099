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

//  Special-case flattener for equality between two expressions that both need
// flattening. Makes one aux variable for one 

public class TransformEqual extends TreeTransformerBottomUpNoWrapper
{
    public TransformEqual(Model _m) {
        super(_m);
    }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Equals)
        {
            ASTNode c1=curnode.getChild(0);
            ASTNode c2=curnode.getChild(1);
            if(c1.toFlatten(false) && c2.toFlatten(false)) {
                if(curnode.getParent().inTopAnd()) {
                    // If top level equality
                    Intpair a=c1.getBounds();
                    Intpair b=c2.getBounds();
                    
                    // Intersection.
                    if(b.lower>a.lower) a.lower=b.lower;
                    if(b.upper<a.upper) a.upper=b.upper;
                    
                    if(a.upper<a.lower) {  // two expressions can't be equal.
                        return new NodeReplacement(new BooleanConstant(false));
                    }
                    if(a.upper==a.lower) { // Both expressions equal a constant.
                        return new NodeReplacement(new And(new Equals(c1, new NumberConstant(a.upper)), 
                            new Equals(c2, new NumberConstant(a.upper))));
                    }
                    
                    // Instead of using newAuxHelper, because we have two expressions it is done manually.
                    ASTNode auxdom=m.filt.constructDomain(c1, a.lower, a.upper);  //  Look up stored (filtered) domain if there is one. Once for each expression.
                    auxdom=m.filt.constructDomain(c2, auxdom);
                    ASTNode auxvar=m.global_symbols.newAuxiliaryVariable(auxdom);
                    
                    m.filt.auxVarRepresentsAST(auxvar.toString(), c1);    // Associate one of the expressions with the aux variable. 
                    
                    m.global_symbols.auxVarRepresentsConstraint( auxvar.toString(), c1.toString()+" --- "+c2.toString());
                    
                    return new NodeReplacement(new And(new ToVariable(c1, auxvar.copy()), 
                        new ToVariable(c2, auxvar.copy())));
                }
                else {
                    // Not top level equality. 
                    
                }
            }
        }
        return null;
    }
}



