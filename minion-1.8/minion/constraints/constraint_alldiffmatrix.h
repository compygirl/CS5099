/*
* Minion http://minion.sourceforge.net
* Copyright (C) 2006-09
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

/** @help constraints;alldiffmatrix Description
For a latin square this constraint is placed on the whole matrix once for each value.
It ensures there is a bipartite matching between rows and columns where the edges
in the matching correspond to a pair (row, column) where the variable in position
(row,column) in the matrix may be assigned to the given value. 
*/

/** @help constraints;alldiffmatrix Example

alldiffmatrix(myVec, Value)
*/

/** @help constraints;alldiffmatrix Notes
This constraint adds some extra reasoning in addition to the GAC Alldifferents
on the rows and columns.
*/



#include <stdlib.h>
#include <iostream>
#include <vector>
#include <deque>
#include <algorithm>
#include <utility>
#include <cmath>

#include "alldiff_gcc_shared.h"


using namespace std;


// for reverse_constraint,
#include "constraint_occurrence.h"
#include "../dynamic_constraints/dynamic_new_or.h"

#define ADMPRINT(x)
//#define ADMPRINT(x) std::cout << x << std::endl;


template<typename VarArrayType, typename ValueType>
struct AlldiffMatrixConstraint : public AbstractConstraint
{
    VarArrayType var_array;

    bool constraint_locked;


    AlldiffMatrixConstraint(StateObj* _stateObj, const VarArrayType& _var_array, const ValueType _value) : AbstractConstraint(_stateObj),
    var_array(_var_array),
    constraint_locked(false),
    value(_value)

    {
        squaresize = (int) sqrt(var_array.size());
        CHECK( (squaresize*squaresize == (SysInt)var_array.size()), "Length of array is not a square.");

        CheckNotBound(var_array, "alldiffmatrix", "no alternative");

        rowcolmatching.resize(squaresize, -1);
        colrowmatching.resize(squaresize, -1);

        prev.resize(squaresize*2);
        visited.reserve(squaresize*2);

        initialize_tarjan();

    }

    int squaresize;

    ValueType value;

    vector<SysInt> rowcolmatching; // For each row, the matching column.
    vector<SysInt> colrowmatching;   // For each column, the matching row.

    virtual string constraint_name()
    {
        return "alldiffmatrix";
    }

    CONSTRAINT_ARG_LIST2(var_array, value);


  SysInt dynamic_trigger_count()
  {
      // Need one per variable on the value of interest, and one on any other value.
      // Two blocks : first the triggers on the value of interest (for all vars) then triggers for assignment.
      // Need to know when assigned to value.
      return var_array.size()*2;
  }

  inline bool hasValue(int row, int col) {
      //   Indexing the latin square from 0
      return var_array[row*squaresize+col].inDomain(value);
  }

  inline bool assignedValue(int row, int col) {
      return var_array[row*squaresize+col].isAssigned()
            && var_array[row*squaresize+col].getAssignedValue()==value;
  }


  typedef typename VarArrayType::value_type VarRef;

  virtual AbstractConstraint* reverse_constraint()
  {
      // use a watched-or of NotOccurrenceEqualConstraint, i.e. the negation of occurrence
        vector<AbstractConstraint*> con;

        ConstantVar one(stateObj, 1);
        for(SysInt i=0; i<squaresize; i++)
        {
            std::vector<VarRef> row_var_array;
            for(SysInt j=0; j<squaresize; j++) row_var_array.push_back(var_array[i*squaresize+j]);

            NotOccurrenceEqualConstraint<VarArrayType, ValueType, ConstantVar>*
                t=new NotOccurrenceEqualConstraint<VarArrayType, ValueType, ConstantVar>(
                    stateObj, row_var_array, value, one);
            con.push_back((AbstractConstraint*) t);
        }

        for(SysInt j=0; j<squaresize; j++)
        {
            std::vector<VarRef> col_var_array;
            for(SysInt i=0; i<squaresize; i++) col_var_array.push_back(var_array[i*squaresize+j]);

            NotOccurrenceEqualConstraint<VarArrayType, ValueType, ConstantVar>*
                t=new NotOccurrenceEqualConstraint<VarArrayType, ValueType, ConstantVar>(
                    stateObj, col_var_array, value, one);
            con.push_back((AbstractConstraint*) t);
        }

        return new Dynamic_OR(stateObj, con);

  }

  virtual void propagate(DynamicTrigger* trig)
  {
      if(trig-dynamic_trigger_start() < (SysInt)var_array.size()) {
          // One of the value has been pruned somewhere
          // Need to propagate.

          SysInt vidx=trig-dynamic_trigger_start();
          SysInt row=vidx/squaresize;
          SysInt col=vidx%squaresize;

          if(rowcolmatching[row]==col) {
              colrowmatching[col]=-1;
              rowcolmatching[row]=-1;
          }

          if(!constraint_locked)
          {
              constraint_locked = true;
              getQueue(stateObj).pushSpecialTrigger(this);
          }
      }
      else {
          D_ASSERT(trig-dynamic_trigger_start() < (SysInt)var_array.size()*2);
          // In the second block. Something was assigned.
          SysInt vidx=trig-dynamic_trigger_start()-(SysInt)var_array.size();


          if(var_array[vidx].getAssignedValue()==value) {
              SysInt row=vidx/squaresize;
              SysInt col=vidx%squaresize;

              bool f=update_matching_assignment(row, col);
              if(!f) {
                  getState(stateObj).setFailed(true);
                  return;
              }

              // If it was assigned to value, then we need to propagate.
              if(!constraint_locked) {
                  constraint_locked = true;
                  getQueue(stateObj).pushSpecialTrigger(this);
              }
          }
      }
  }

  virtual void special_unlock() { constraint_locked = false; }

  virtual void special_check()
  {
    constraint_locked = false;

    if(getState(stateObj).isFailed())
    {
        return;
    }
    do_prop();
  }


  virtual void full_propagate()
  {
      // Clear the two matching arrays
      for(int i=0; i<squaresize; i++) {
          rowcolmatching[i]=-1;
          colrowmatching[i]=-1;
      }

      // Set up triggers.

      for(int i=0; i < (SysInt)var_array.size(); i++) {
          if(var_array[i].inDomain(value)) {
              var_array[i].addDynamicTrigger(dynamic_trigger_start()+i, DomainRemoval, value);
              var_array[i].addDynamicTrigger(dynamic_trigger_start()+i+var_array.size(), Assigned);
          }

          if(var_array[i].isAssigned() && var_array[i].getAssignedValue()==value) {
              SysInt row=i/squaresize;
              SysInt col=i%squaresize;

              // If two assignments in one row or column...
              if(rowcolmatching[row]!=-1  ||  colrowmatching[col]!=-1) {
                  getState(stateObj).setFailed(true);
                  return;
              }
              else {
                  rowcolmatching[row]=col;
                  colrowmatching[col]=row;
              }
          }
      }


      if(getState(stateObj).isFailed())
      {
          return;
      }
      do_prop();
  }


    virtual BOOL check_assignment(DomainInt* v, SysInt array_size)
    {
        D_ASSERT(array_size == (SysInt)var_array.size());
        for(SysInt i=0; i<squaresize; i++) {
            SysInt count=0;
            for(SysInt j=0; j<squaresize; j++) if(v[i*squaresize+j]==value) count++;

            if(count!=1) return false;

            count=0;
            for(SysInt j=0; j<squaresize; j++) if(v[j*squaresize+i]==value) count++;
            if(count!=1) return false;
        }
        return true;
    }

    virtual vector<AnyVarRef> get_vars()
    {
      vector<AnyVarRef> vars;
      vars.reserve(var_array.size());
      for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
        vars.push_back(var_array[i]);
      return vars;
    }



    //   Above is basically interface with Minion, below is the flow algo

    void do_prop()
    {
        bool matchok=bfsmatching();

        if(!matchok) {
            getState(stateObj).setFailed(true);
        }
        else {
            tarjan_recursive();
        }
    }


    vector<SysInt> augpath;
    deque<SysInt> fifo;

    vector<SysInt> prev;

    smallset_nolist visited;


    inline bool update_matching_assignment(int row, int col) {
        D_ASSERT(assignedValue(row, col));

        if(rowcolmatching[row]!=col) {
            // Could be the row or column is already assigned
            if(rowcolmatching[row]!=-1 && assignedValue(row, rowcolmatching[row])) {
                return false;
            }

            if(colrowmatching[col]!=-1 && assignedValue(colrowmatching[col], col)) {
                return false;
            }

            // First free up row and col.
            if(rowcolmatching[row]>-1) {
                colrowmatching[rowcolmatching[row]]=-1;
                // rowcolmatching[row]=-1;    // not necessary
            }
            if(colrowmatching[col]>-1) {
                rowcolmatching[colrowmatching[col]]=-1;
                // colrowmatching[col]=-1;    // not necessary
            }

            rowcolmatching[row]=col;
            colrowmatching[col]=row;

        }
        return true;
    }

    inline bool bfsmatching()
    {
        // This function assumes the matching has been 'cleaned up'
        // i.e. there are no matching edges if the value is not in domain,
        // and if the variable is assigned to value then the edge is in the matching.

        D_DATA(
        for(int row=0; row<squaresize; row++) {
            for(int col=0; col<squaresize; col++) {
                if(rowcolmatching[row]==col) {
                    D_ASSERT(colrowmatching[col]==row);
                }

                if(! hasValue(row, col)) {
                    D_ASSERT(rowcolmatching[row]!=col);
                    D_ASSERT(colrowmatching[col]!=row);
                }

                if( assignedValue(row, col)) {
                    D_ASSERT(rowcolmatching[row]==col);
                    D_ASSERT(colrowmatching[col]==row);
                }
            }
        })

        // iterate through the matching looking for broken matches.

        for(SysInt initialrow=0; initialrow<squaresize; initialrow++) {
            if(rowcolmatching[initialrow]==-1) {
                // starting from a row, find a path to a free column.

                // No adjacency lists.

                fifo.clear();  // this should be constant time but probably is not.

                fifo.push_back(initialrow);

                visited.clear();
                visited.insert(initialrow);

                bool finished=false;
                while(!fifo.empty() && !finished) {
                    SysInt curnode=fifo.front();
                    fifo.pop_front();

                    if(curnode<squaresize) {
                        // It's a row.
                        // Iterate through columns connected to this row.
                        for(int col=0; col<squaresize; col++) {
                            if(!visited.in(col+squaresize) && hasValue(curnode, col)) {
                                // If we got to curnode along a matching edge,
                                // then the
                                visited.insert(col+squaresize);
                                prev[col+squaresize]=curnode;
                                fifo.push_back(col+squaresize);
                            }
                        }
                    }
                    else {
                        // curnode is a column.
                        SysInt newnode=colrowmatching[curnode-squaresize];

                        if(newnode==-1) {
                            // This column is not matched with anything.
                            finished=true;
                            apply_augmenting_path(curnode, initialrow);
                        }
                        else {
                            // If the row/col is assigned to value, then we can't traverse the reverse edge.
                            if(!assignedValue(newnode, curnode-squaresize)) {
                                if(!visited.in(newnode)) {
                                    visited.insert(newnode);
                                    prev[newnode]=curnode;
                                    fifo.push_back(newnode);
                                }
                            }
                        }
                    }

                }

                if(!finished) return false;    // No complete matching


            }
        }
        return true;
    }


    inline void apply_augmenting_path(SysInt unwindnode, SysInt startnode)
    {
        augpath.clear();
        // starting at unwindnode, unwind the path and put it in augpath.
        // Then apply it.
        // Assumes prev contains vertex numbers.
        SysInt curnode=unwindnode;
        while(curnode!=startnode)
        {
            augpath.push_back(curnode);
            curnode=prev[curnode];
        }
        augpath.push_back(curnode);

        std::reverse(augpath.begin(), augpath.end());

        ADMPRINT("Found augmenting path:" << augpath);

        // now apply the path.
        for(SysInt i=0; i<(SysInt)augpath.size()-1; i++)
        {
            curnode=augpath[i];
            if(curnode<squaresize)
            {
                // if it's a row:
                // Overwrites existing edges using same row or column.
                rowcolmatching[curnode]=augpath[i+1]-squaresize;   // convert next node to a column number.
                colrowmatching[augpath[i+1]-squaresize]=curnode;
            }
            else
            {
                // it's a column. Do nothing.
            }
        }

        ADMPRINT("rowcolmatching: "<<rowcolmatching);
        ADMPRINT("colrowmatching: "<<colrowmatching);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    //   Tarjan's algorithm.
    //   Edges in matching map from row to column. Edges not in matching map
    //   from column to row.

    vector<SysInt> tstack;
    smallset_nolist in_tstack;

    vector<SysInt> dfsnum;
    vector<SysInt> lowlink;

    vector<SysInt> curnodestack;

    SysInt max_dfs;

    SysInt varcount, valcount;
    //SysInt localmin,localmax;

    smallset_nolist rowinscc;


    void initialize_tarjan()
    {
        tstack.reserve(squaresize*2);
        in_tstack.reserve(squaresize*2);
        //visited.reserve(squaresize*2);
        max_dfs=1;
        dfsnum.resize(squaresize*2);
        lowlink.resize(squaresize*2);

        curnodestack.reserve(squaresize*2);

        rowinscc.reserve(squaresize);
    }

    void tarjan_recursive() {
        tstack.clear();
        in_tstack.clear();

        visited.clear();
        max_dfs=1;

        // Make sure all rows and columns are visited by Tarjans.

        for(SysInt row=0; row<squaresize; row++)
        {
            if(!visited.in(row))
            {
                ADMPRINT("(Re)starting tarjan's algorithm, at node:"<< row);
                visit(row, true);
                ADMPRINT("Returned from tarjan's algorithm.");
            }
        }

        for(SysInt col=0; col<squaresize; col++) {
            if(!visited.in(col+squaresize))
            {
                ADMPRINT("(Re)starting tarjan's algorithm, at node:"<< col+squaresize);
                visit(col+squaresize, true);
                ADMPRINT("Returned from tarjan's algorithm.");
            }
        }

    }

    void visit(SysInt curnode, bool toplevel)
    {
        // toplevel is true iff this is the top level of the recursion.
        tstack.push_back(curnode);
        in_tstack.insert(curnode);
        dfsnum[curnode]=max_dfs;
        lowlink[curnode]=max_dfs;
        max_dfs++;
        visited.insert(curnode);
        ADMPRINT("Visiting node: " <<curnode);

        if(curnode<squaresize)
        {
            // Not allowed to traverse this edge if it is 'assigned' (i.e.
            // fixed to value 1), because it may be traversed only if it can be
            // reversed.
            if(!assignedValue(curnode, rowcolmatching[curnode])) {
                SysInt newnode=rowcolmatching[curnode]+squaresize;
                if(!visited.in(newnode))
                {
                    visit(newnode, false);
                    if(lowlink[newnode]<lowlink[curnode])
                    {
                        lowlink[curnode]=lowlink[newnode];
                    }
                }
                else
                {
                    // Already visited newnode
                    if(in_tstack.in(newnode) && dfsnum[newnode]<lowlink[curnode])
                    {
                        lowlink[curnode]=dfsnum[newnode];  // Why dfsnum not lowlink?
                    }
                }
            }
        }
        else
        {
            // curnode is a column
            SysInt col=curnode-squaresize;

            for(SysInt row=0; row<squaresize; row++)
            {
                if(rowcolmatching[row]!=col)   // if the edge is not in the matching.
                {
                    if(hasValue(row, col))     // and the value
                    {
                        if(!visited.in(row))
                        {
                            visit(row, false);
                            if(lowlink[row]<lowlink[curnode])
                            {
                                lowlink[curnode]=lowlink[row];
                            }
                        }
                        else
                        {
                            // Already visited row
                            if(in_tstack.in(row) && dfsnum[row]<lowlink[curnode])
                            {
                                lowlink[curnode]=dfsnum[row];
                            }
                        }
                    }
                }
            }
        }

        //cout << "On way back up, curnode:" << curnode<< ", lowlink:"<<lowlink[curnode]<< ", dfsnum:"<<dfsnum[curnode]<<endl;
        if(lowlink[curnode]==dfsnum[curnode])
        {
            // At the root of a strongly connected component.
            rowinscc.clear();

            for(vector<SysInt>::iterator tstackit=(tstack.end()-1);  ; --tstackit)
            {
                SysInt copynode=(*tstackit);
                if(copynode<squaresize)   // It's a row.
                {
                    rowinscc.insert(copynode);
                }

                if(copynode==curnode)
                {
                    break;
                }
            }

            while(true)
            {
                SysInt copynode=(*(tstack.end()-1));

                tstack.pop_back();
                in_tstack.remove(copynode);

                if(copynode>=squaresize)
                {
                    // It's a column. Iterate through rows and prune whenever the row is not in same scc as this column.

                    for(SysInt row=0; row<squaresize; row++)
                    {
                        if(!rowinscc.in(row))
                        {

                            if(rowcolmatching[row]!=copynode-squaresize) {
                                ADMPRINT("Pruning row: " << row << " column: "<< copynode-squaresize << " removing value." );
                                var_array[row*squaresize + copynode-squaresize].removeFromDomain(value);
                            }
                            else {
                                ADMPRINT("Setting row: " << row << " column: "<< copynode-squaresize << " to value.");
                                var_array[row*squaresize + copynode-squaresize].propagateAssign(value);
                            }
                        }
                    }
                }

                if(copynode==curnode)
                {
                    break;
                }
            }


        }
    }



  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
      // Clean up the matching first.
      for(int row=0; row<squaresize; row++) {
          if(rowcolmatching[row]>-1 && ! hasValue(row, rowcolmatching[row])) {
              colrowmatching[rowcolmatching[row]]=-1;
              rowcolmatching[row]=-1;
          }
      }

      for(int row=0; row<squaresize; row++) {
          for(int col=0; col<squaresize; col++) {
              if(assignedValue(row, col)) {
                  bool f=update_matching_assignment(row, col);
                  if(!f) return false;
              }
          }
      }

      bool matchok=bfsmatching();

      ADMPRINT(rowcolmatching);
      ADMPRINT(colrowmatching);

      if(!matchok) return false;

      for(SysInt row=0; row<squaresize; row++) {
          for(SysInt col=0; col<squaresize; col++) {
              if(rowcolmatching[row]==col) {
                  assignment.push_back(make_pair((row*squaresize)+col, value));
              }
              else {
                  if(var_array[row*squaresize+col].getMax()!=value) {
                      assignment.push_back(make_pair((row*squaresize)+col, var_array[row*squaresize+col].getMax()));
                  }
                  else {
                      assignment.push_back(make_pair((row*squaresize)+col, var_array[row*squaresize+col].getMin()));
                  }
              }
          }
      }
      return true;
  }






};
