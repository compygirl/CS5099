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

//
//  Flattened Mapping to table.

public class TransformMappingToTable extends TreeTransformerBottomUpNoWrapper
{
    public TransformMappingToTable() { super(null); }
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof ToVariable && curnode.getChild(0) instanceof Mapping)
        {
            ASTNode tablevars=CompoundMatrix.makeCompoundMatrix(curnode.getChild(0).getChild(0), curnode.getChild(1));
            
            ArrayList<ASTNode> table=new ArrayList<ASTNode>();
            
            Intpair a=curnode.getChild(0).getChild(0).getBounds();
            
            HashMap<Long,Long> map=((Mapping)curnode.getChild(0)).map;
            long defaultval=((Mapping)curnode.getChild(0)).defaultval;
            
            for(long val=a.lower; val<=a.upper; val++) {
                if(map.containsKey(val)) {
                    table.add(CompoundMatrix.makeCompoundMatrix(new NumberConstant(val), new NumberConstant(map.get(val))));
                }
                else {
                    table.add(CompoundMatrix.makeCompoundMatrix(new NumberConstant(val), new NumberConstant(defaultval)));
                }
            }
            return new NodeReplacement(new Table(tablevars, CompoundMatrix.makeCompoundMatrix(table)));
        }
        return null;
    }
}

