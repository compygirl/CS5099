#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net

   For Licence Information see file LICENSE.txt
*/

#include "../dynamic_constraints/dynamic_sum.h"

template<typename T>
inline AbstractConstraint*
  BuildCT_WATCHED_GEQSUM(StateObj* stateObj,const vector<T>& t1, ConstraintBlob& b)
{
  for(SysInt i = 0; i < (SysInt)t1.size(); ++i)
  {
    if(t1[i].getInitialMin() < 0 || t1[i].getInitialMax() > 1)
      FAIL_EXIT("watched geqsum only works on Boolean variables!");
  }

    DomainInt sum = b.constants[0][0];
    if(sum == 1)
    {
      {
        return BoolSATConDynamic(stateObj, t1);
      }
    }
    else
    {
      return BoolGreaterEqualSumConDynamic(stateObj, t1, sum);
    }

}

BUILD_CT(CT_WATCHED_GEQSUM, 1)
