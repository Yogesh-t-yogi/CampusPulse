import { useEffect, useState } from "react";
import axios from "axios";

function Curriculum() {

  const [syllabus, setSyllabus] = useState(null);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");


  useEffect(() => {

    fetchCurriculum();

  }, []);


  const fetchCurriculum = async () => {

    try {

      const response = await axios.get(
        "http://localhost:8081/api/syllabus/4/full"
      );

      setSyllabus(response.data);

    } catch (error) {

      console.error(error);

      setError("Failed to load curriculum.");

    } finally {

      setLoading(false);

    }

  };


  if (loading) {

    return (
      <div>
        <h1>Curriculum</h1>

        <p>Loading curriculum...</p>
      </div>
    );

  }


  if (error) {

    return (
      <div>
        <h1>Curriculum</h1>

        <p>{error}</p>
      </div>
    );

  }


  return (

    <div>

      <h1>Curriculum</h1>

      <p>
        Explore your syllabus, subjects, units and topics.
      </p>


      {syllabus && (

        <div>

          <h2>
            {syllabus.name}
          </h2>


          <p>
            File: {syllabus.fileName}
          </p>


          <p>
            Subjects: {syllabus.subjects?.length || 0}
          </p>


          {syllabus.subjects?.map((subject) => (

            <div key={subject.id}>

              <h2>
                {subject.name}
              </h2>


              <p>
                Code: {subject.code}
              </p>


              {subject.units?.map((unit) => (

                <div key={unit.id}>

                  <h3>
                    Unit {unit.unitNumber}: {unit.name}
                  </h3>


                  {unit.topics?.map((topic) => (

                    <p key={topic.id}>
                      • {topic.name}
                    </p>

                  ))}

                </div>

              ))}

            </div>

          ))}

        </div>

      )}

    </div>

  );
}


export default Curriculum;