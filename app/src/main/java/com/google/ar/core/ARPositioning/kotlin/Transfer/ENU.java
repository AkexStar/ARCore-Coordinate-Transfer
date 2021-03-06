package com.google.ar.core.ARPositioning.kotlin.Transfer;

import static java.lang.Math.atan;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import org.locationtech.jts.geom.Coordinate;

/**
 * Conversion of Geodetic coordinates to the Local Tangent Plane.
 *
 * <p>Class that supports WGS84 to East-North-Up conversion. The conversions
 * reference the base coordinate that is given at construction time.</p>
 *
 * <p>Math is available in the paper: Conversion of Geodetic coordinates to the
 * Local Tangent Plane.</p>
 *
 * <p>The ecefToWgs84 method adapted from the goGPS project.</p>
 *
 * <p>Note that all coordinates need to have a Z available. If the Z is NaN, then it is set to 0.</p>
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class ENU {

    private static double semiMajorAxis = 6378137.0;
    private static double smeiMinorAxis = 6356752.3142;
    private static double flatness = (semiMajorAxis - smeiMinorAxis) / semiMajorAxis;
    private static double eccentricityP2 = flatness * (2 - flatness);

    private Coordinate _baseCoordinateLL;
    private double _lambda;
    private double _phi;
    private double _sinLambda;
    private double _cosLambda;
    private double _cosPhi;
    private double _sinPhi;
    private double _N;
    private RealMatrix _rotationMatrix;
    private double _ecefROriginX;
    private double _ecefROriginY;
    private double _ecefROriginZ;
    private RealMatrix _inverseRotationMatrix;

    /**
     * Create a new East North Up system.
     *
     * @param baseCoordinateLL the WGS84 coordinate to use a origin of the ENU.
     */
    public ENU( Coordinate baseCoordinateLL ) {
        checkZ(baseCoordinateLL);
        this._baseCoordinateLL = baseCoordinateLL;
        _lambda = toRadians(baseCoordinateLL.y);
        _phi = toRadians(baseCoordinateLL.x);
        _sinLambda = sin(_lambda);
        _cosLambda = cos(_lambda);
        _cosPhi = cos(_phi);
        _sinPhi = sin(_phi);
        _N = semiMajorAxis / sqrt(1 - eccentricityP2 * pow(_sinLambda, 2.0));

        double[][] rot = new double[][]{//
                {-_sinPhi, _cosPhi, 0}, //
                {-_cosPhi * _sinLambda, -_sinLambda * _sinPhi, _cosLambda}, //
                {_cosLambda * _cosPhi, _cosLambda * _sinPhi, _sinLambda},//
        };
        _rotationMatrix = MatrixUtils.createRealMatrix(rot);
        _inverseRotationMatrix = new LUDecomposition(_rotationMatrix).getSolver().getInverse();

        // the origin of the LTP expressed in ECEF-r coordinates
        double h = _baseCoordinateLL.z;
        _ecefROriginX = (h + _N) * _cosLambda * _cosPhi;
        _ecefROriginY = (h + _N) * _cosLambda * _sinPhi;
        _ecefROriginZ = (h + (1 - eccentricityP2) * _N) * _sinLambda;
    }

    /**
     * Converts the wgs84 coordinate to ENU.
     *
     * @param cLL the wgs84 coordinate.
     * @return the ENU coordinate.
     * @throws MatrixException
     */
    public Coordinate wgs84ToEnu( Coordinate cLL ) {
        checkZ(cLL);
        Coordinate cEcef = wgs84ToEcef(cLL);
        Coordinate enu = ecefToEnu(cEcef);
        return enu;
    }

    /**
     * Converts the ENU coordinate to wgs84.
     *
     * @param enu the ENU coordinate.
     * @return the wgs84 coordinate.
     * @throws MatrixException
     */
    public Coordinate enuToWgs84( Coordinate enu ) {
        checkZ(enu);
        Coordinate cEcef = enuToEcef(enu);
        Coordinate wgs84 = ecefToWgs84(cEcef);
        return wgs84;
    }

    /**
     * Converts WGS84 coordinates to Earth-Centered Earth-Fixed (ECEF) coordinates.
     *
     * @param cLL the wgs84 coordinate.
     * @return the ecef coordinate.
     */
    public Coordinate wgs84ToEcef( Coordinate cLL ) {
        double lambda = toRadians(cLL.y);
        double phi = toRadians(cLL.x);
        double sinLambda = sin(lambda);
        double cosLambda = cos(lambda);
        double cosPhi = cos(phi);
        double sinPhi = sin(phi);
        double N = semiMajorAxis / sqrt(1 - eccentricityP2 * pow(sinLambda, 2.0));

        double h = cLL.z;
        double x = (h + N) * cosLambda * cosPhi;
        double y = (h + N) * cosLambda * sinPhi;
        double z = (h + (1 - eccentricityP2) * N) * sinLambda;
        return new Coordinate(x, y, z);
    }

    /**
     * Converts an Earth-Centered Earth-Fixed (ECEF) coordinate to ENU.
     *
     * @param cEcef the ECEF coordinate.
     * @return the ENU coordinate.
     * @throws MatrixException
     */
    public Coordinate ecefToEnu( Coordinate cEcef ) {
        double deltaX = cEcef.x - _ecefROriginX;
        double deltaY = cEcef.y - _ecefROriginY;
        double deltaZ = cEcef.z - _ecefROriginZ;

        double[][] deltas = new double[][]{{deltaX}, {deltaY}, {deltaZ}};
        RealMatrix deltaMatrix = MatrixUtils.createRealMatrix(deltas);

        RealMatrix enuMatrix = _rotationMatrix.multiply(deltaMatrix);
        double[] column = enuMatrix.getColumn(0);
        return new Coordinate(column[0], column[1], column[2]);
    }

    /**
     * Converts an ENU coordinate to Earth-Centered Earth-Fixed (ECEF).
     *
     * @param cEnu the enu coordinate.
     * @return the ecef coordinate.
     */
    public Coordinate enuToEcef( Coordinate cEnu ) {
        double[][] enu = new double[][]{{cEnu.x}, {cEnu.y}, {cEnu.z}};
        RealMatrix enuMatrix = MatrixUtils.createRealMatrix(enu);

        RealMatrix deltasMatrix = _inverseRotationMatrix.multiply(enuMatrix);

        double[] column = deltasMatrix.getColumn(0);
        double cecfX = column[0] + _ecefROriginX;
        double cecfY = column[1] + _ecefROriginY;
        double cecfZ = column[2] + _ecefROriginZ;

        return new Coordinate(cecfX, cecfY, cecfZ);
    }

    /**
     * Converts a Earth-Centered Earth-Fixed (ECEF) coordinate to WGS84.
     *
     * @param ecef the ecef coordinate.
     * @return the wgs84 coordinate.
     */
    public Coordinate ecefToWgs84( Coordinate ecef ) {
        // Radius computation
        double r = sqrt(pow(ecef.x, 2) + pow(ecef.y, 2) + pow(ecef.z, 2));
        // Geocentric longitude
        double lamGeoc = atan2(ecef.y, ecef.x);
        // Geocentric latitude
        double phiGeoc = atan(ecef.z / sqrt(pow(ecef.x, 2) + pow(ecef.y, 2)));
        // Computation of geodetic coordinates
        double psi = atan(tan(phiGeoc) / sqrt(1 - eccentricityP2));
        double phiGeod = atan((r * sin(phiGeoc) + eccentricityP2 * semiMajorAxis / sqrt(1 - eccentricityP2) * pow(sin(psi), 3))
                / (r * cos(phiGeoc) - eccentricityP2 * semiMajorAxis * pow(cos(psi), 3)));
        double lamGeod = lamGeoc;
        double N = semiMajorAxis / sqrt(1 - eccentricityP2 * pow(sin(phiGeod), 2));
        double h = r * cos(phiGeoc) / cos(phiGeod) - N;

        double lon = toDegrees(lamGeod);
        double lat = toDegrees(phiGeod);
        return new Coordinate(lon, lat, h);
    }

    private void checkZ( Coordinate coordinate ) {
        if (Double.isNaN(coordinate.z)) {
            coordinate.z = 0.0;
        }
        //
    }
}