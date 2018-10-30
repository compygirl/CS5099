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

import java.util.ArrayList;
import java.util.HashMap;

//  Turn alldiff except into a GCC.

public class TransformAlldiffExcept extends TreeTransformerBottomUpNoWrapper
{
    public TransformAlldiffExcept(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof AllDifferentExcept)
        {
            Intpair bnd=curnode.getChild(0).getBounds();
            
            if(curnode.getChild(1).getCategory()>ASTNode.Constant) {
                CmdFlags.errorExit("Second parameter of AlldifferentExcept is not a constant:" +curnode.getChild(1));
            }
            
            long specialval=curnode.getChild(1).getValue();
            if(bnd.lower>specialval || bnd.upper<specialval) {
                return new NodeReplacement(new AllDifferent(curnode.getChild(0)));
            }
            
            ArrayList<ASTNode> mat=new ArrayList<ASTNode>();
            ArrayList<ASTNode> occs=new ArrayList<ASTNode>();
            
            for(long i=bnd.lower; i<=bnd.upper; i++) {
                if(i!=specialval) {
                    mat.add(new NumberConstant(i));
                    occs.add(m.global_symbols.newAuxiliaryVariable(0,1));
                }
            }
            ASTNode vals=CompoundMatrix.makeCompoundMatrix(mat);
            
            ASTNode occurrences=CompoundMatrix.makeCompoundMatrix(occs);
            return new NodeReplacement(new GlobalCard(curnode.getChild(0), vals, occurrences));
        }
        return null;
    }
}

