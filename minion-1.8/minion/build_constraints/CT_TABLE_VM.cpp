#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net

   For Licence Information see file LICENSE.txt
*/

#ifdef _WIN32

template <typename T>
AbstractConstraint*
BuildCT_TABLE_VM(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return NULL; }

BUILD_CT(CT_TABLE_VM, 1)

template <typename T>
AbstractConstraint*
BuildCT_NEG_TABLE_VM(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return NULL; }

BUILD_CT(CT_NEG_TABLE_VM, 1)

#else

#include "../constraints/vm.h"
#include "../constraints/constraint_constant.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "../constraints/constraint_GACtable_master.h"
#include "../inputfile_parse/tiny_constraint_parser.hpp"

#include "../constraints/constraint_mddc.h"

#ifdef CHECK_TABLE
#define READ_TABLE
#endif

template<typename T>
std::string make_table_string(const T& t1, ConstraintBlob& b, char const* type)
{
    std::ostringstream oss;
    oss << "{\n";
    oss << "\"doms\" : [";
    for(SysInt i = 0; i < (SysInt)t1.size(); ++i)
    {
        if(i > 0) oss <<  ",";
        oss << "[";
        oss << checked_cast<SysInt>(t1[i].getInitialMin());
        for(DomainInt j = t1[i].getInitialMin() + 1; j <= t1[i].getInitialMax(); ++j)
        {
            oss << "," << checked_cast<SysInt>(j);
        }
        oss << "]";
    }
    oss << "],\n";

    oss <<  "\"table\" : [";
    for(SysInt i = 0; i < (DomainInt)b.tuples->size(); ++i)
    {
        vector<DomainInt> v = b.tuples->get_vector(i);
        if(i > 0) oss <<  ",\n";
        oss << "[";
        oss << checked_cast<SysInt>(v[0]);
        for(SysInt j = 1; j < (SysInt)v.size(); ++j)
        {
            oss << "," << checked_cast<SysInt>(v[j]);
        }
        oss << "]";
    }
    oss << "],\n";

    oss <<  "\"type\" : \"" << type << "\" }\n";

    return oss.str();
}

#ifdef READ_TABLE




template <typename T>
AbstractConstraint*
read_table_vm(StateObj* stateObj,const T& t1, ConstraintBlob& b, char const* type)
{
    // Store a list of vms we have already loaded.
    static std::map<std::string, std::vector<TupleList*> > cached_vms;

    std::string s = make_table_string(t1, b, type);

    std::string hash = sha1_hash(s);

    std::string outname = "table." + hash + ".vm";

    vector<TupleList*> vtl;

    if(cached_vms.count(hash) > 0)
    {
#ifdef CHECK_TABLE
        std::cout << "# Match cached table: '" + hash + "'\n";
#endif
        vtl = cached_vms[hash];
    }
    else
    {
        ifstream ifs(outname.c_str(), ios::in);
        if(!ifs)
        {
#ifdef CHECK_TABLE
            std::cout << "# No table match: '" + hash + "'\n";
#endif
            // Opening file failed
            if(std::string(type) == "pos") {
                // return GACTableCon(stateObj, t1, b.tuples);
                return new MDDC<T>(stateObj, t1, b.tuples);
            }
            if(std::string(type) == "neg") {
                // return GACNegativeTableCon(stateObj, t1, b.tuples);
                return new MDDC<T, true>(stateObj, t1, b.tuples);
            }
            abort();
        }
#ifdef CHECK_TABLE
        std::cout << "# Match fresh table: '" + hash + "'\n";
#endif

        vtl = tiny_parser(ifs);
    }

    cached_vms[hash] = vtl;

    return VMSymCon(stateObj, t1, vtl[0], vtl[1]);
}


template <typename T>
AbstractConstraint*
BuildCT_TABLE_VM(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return read_table_vm(stateObj, t1, b, "pos"); }

BUILD_CT(CT_TABLE_VM, 1)

template <typename T>
AbstractConstraint*
BuildCT_NEG_TABLE_VM(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return read_table_vm(stateObj, t1, b, "neg"); }

BUILD_CT(CT_NEG_TABLE_VM, 1)


#else


template <typename T>
AbstractConstraint*
output_table_vm(StateObj* stateObj,const T& t1, ConstraintBlob& b, char const* type)
{
    std::string s = make_table_string(t1, b, type);

    std::string hash = sha1_hash(s);

    std::string outname = "tableout/table." + hash + ".vm";
    std::string countname = "tableout/table." + hash + ".count";

    int fd = open(countname.c_str(), O_CREAT|O_APPEND|O_WRONLY, S_IWUSR);
    if(fd >= 0)
    {
        char buf[] = ".";
        (void)write(fd, buf, 1);
        close(fd);
    }


    int f = open(outname.c_str(), O_RDWR | O_CREAT | O_EXCL, S_IRWXU);
    if(f >= 0)
    {
        write(f, s.c_str(), s.size());
        close(f);
    }
    return (new ConstantConstraint<false>(stateObj));

}


template <typename T>
AbstractConstraint*
BuildCT_TABLE_VM(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return output_table_vm(stateObj, t1, b, "pos"); }

BUILD_CT(CT_TABLE_VM, 1)

template <typename T>
AbstractConstraint*
BuildCT_NEG_TABLE_VM(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return output_table_vm(stateObj, t1, b, "neg"); }

BUILD_CT(CT_NEG_TABLE_VM, 1)

#endif // _WIN32

#endif
