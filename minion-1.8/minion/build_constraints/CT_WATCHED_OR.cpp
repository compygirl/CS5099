#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net

   For Licence Information see file LICENSE.txt
*/

#include "../dynamic_constraints/dynamic_satclause.h"

template<typename T>
inline AbstractConstraint*
BuildCT_WATCHED_OR(StateObj* stateObj, const vector<T>& vs, ConstraintBlob& bl)
{
  SysInt vs_s = vs.size();
  for(SysInt i = 0; i < vs_s; i++)
    if(vs[i].getInitialMin() < 0 || vs[i].getInitialMax() > 1)
    {
      FAIL_EXIT("watched or only works on Boolean variables!");
    }

  return new BoolOrConstraintDynamic<vector<T> >(stateObj, vs, bl.negs);
}

BUILD_CT(CT_WATCHED_OR, 1)
