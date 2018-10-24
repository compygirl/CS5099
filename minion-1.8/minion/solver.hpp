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

namespace Controller
{

inline SysInt get_world_depth(StateObj* stateObj)
{ return getMemory(stateObj).backTrack().current_depth(); }

/// Pushes the state of the whole world.
inline void world_push(StateObj* stateObj)
{
  getQueue(stateObj).getTbq().world_push();
  getMemory(stateObj).monotonicSet().before_branch_left();
  D_ASSERT(getQueue(stateObj).isQueuesEmpty());
  getMemory(stateObj).backTrack().world_push();
  getMemory(stateObj).monotonicSet().after_branch_left();
  getState(stateObj).getConstraintsToPropagate().push_back(set<AbstractConstraint*>());
  getState(stateObj).getGenericBacktracker().mark();
}

/// Pops the state of the whole world.
inline void world_pop(StateObj* stateObj)
{
  D_ASSERT(getQueue(stateObj).isQueuesEmpty());
  getState(stateObj).getGenericBacktracker().world_pop();
  getMemory(stateObj).backTrack().world_pop();
  getMemory(stateObj).monotonicSet().undo();
  getQueue(stateObj).getTbq().world_pop();

  vector<set<AbstractConstraint*> >& constraintList = getState(stateObj).getConstraintsToPropagate();
  SysInt propagateDepth = get_world_depth(stateObj) + 1;
  if((SysInt)constraintList.size() > propagateDepth)
  {
    for(set<AbstractConstraint*>::iterator it = constraintList[propagateDepth].begin();
        it != constraintList[propagateDepth].end(); it++)
    {
      (*it)->full_propagate();
    }

    if(propagateDepth > 0)
    {
      constraintList[propagateDepth - 1].insert(constraintList[propagateDepth].begin(), constraintList[propagateDepth].end());
    }
    constraintList.pop_back();
  }
}


inline void world_pop_to_depth(StateObj* stateObj, SysInt depth)
{
  // TODO: Speed up this method. It shouldn't call world_pop repeatedly.
  // The main problem is this requires adding additions to things like
  // monotonic sets I suspect.
  D_ASSERT(depth <= get_world_depth(stateObj));
  while(depth < get_world_depth(stateObj))
    world_pop(stateObj);
}

inline void world_pop_all(StateObj* stateObj)
{
SysInt depth = getMemory(stateObj).backTrack().current_depth();
for(; depth > 0; depth--)
  world_pop(stateObj);
}

}

inline void SearchState::addConstraint(AbstractConstraint* c)
{
  constraints.push_back(c);
  vector<AnyVarRef>* vars = c->get_vars_singleton();
  size_t vars_s = vars->size();
  for(size_t i = 0; i < vars_s; i++) //note all constraints the var is involved in
    (*vars)[i].addConstraint(c);
}

inline void SearchState::addConstraintMidsearch(AbstractConstraint* c)
{
  addConstraint(c);
  c->setup();
  redoFullPropagate(c);
}

inline void SearchState::redoFullPropagate(AbstractConstraint* c)
{
  constraints_to_propagate[Controller::get_world_depth(stateObj)].insert(c);
  c->full_propagate();
}
