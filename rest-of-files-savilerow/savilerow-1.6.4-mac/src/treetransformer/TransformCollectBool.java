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

//  Collect all places where a boolean variable is used in  a boolean or integer context -- 
// for gecode output.
// Must be used after matrices are taken apart.   Needs to be updated when new types
// are added as ASTNodes.

public class TransformCollectBool extends TreeTransformerBottomUpNoWrapper
{
    public TransformCollectBool(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Identifier)
        {
            ASTNode dom=m.global_symbols.getDomain(curnode.toString());
            if(dom.equals(new BooleanDomain()) || dom.equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1))))) {
                // mark if it's in a boolean or integer context. 
                
                if(curnode.getParent() instanceof ToVariable) {
                    if(curnode.getParent().getChild(0).isRelation()) {
                        m.global_symbols.markAsBoolGecode(curnode.toString());
                    }
                    else {
                        m.global_symbols.markAsIntGecode(curnode.toString());
                    }
                    return null;
                }
                
                // Lexleq/lexless experiment
                /*if(curnode.getParent() instanceof CompoundMatrix && 
                    (curnode.getParent().getParent() instanceof LexLess ||
                        curnode.getParent().getParent() instanceof LexLessEqual)) {
                    ASTNode lex=curnode.getParent().getParent();
                    boolean boolchildren=lex.getChild(0).isRelation() && lex.getChild(1).isRelation();
                    if(boolchildren) {
                        m.global_symbols.markAsBoolGecode(curnode.toString());
                    }
                    else {
                        m.global_symbols.markAsIntGecode(curnode.toString());
                    }
                    return null;
                }*/
                
                // List contexts that require boolean children
                if(curnode.getParent() instanceof And || curnode.getParent() instanceof Or
                    || curnode.getParent() instanceof Implies || curnode.getParent() instanceof Negate
                    || curnode.getParent() instanceof Iff ) {
                    m.global_symbols.markAsBoolGecode(curnode.toString());
                }
                else {
                    m.global_symbols.markAsIntGecode(curnode.toString());
                }
            }
        }
        return null;
    }
}

