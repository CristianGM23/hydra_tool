import { describe, expect, it } from 'vitest';
import { constrainChildToShape, pointInsideShape, shapePolygon } from './editorNotationGeometry';

describe('graphical notation contour geometry',()=>{
    it('supports the extended polygon library',()=>{
        expect(shapePolygon('TRIANGLE')).toHaveLength(3);
        expect(shapePolygon('PENTAGON')).toHaveLength(5);
        expect(shapePolygon('OCTAGON')).toHaveLength(8);
        expect(shapePolygon('STAR')).toHaveLength(10);
    });
    it('distinguishes the real diamond contour from its bounding rectangle',()=>{
        expect(pointInsideShape('DIAMOND',{x:50,y:50},100,100)).toBe(true);
        expect(pointInsideShape('DIAMOND',{x:5,y:5},100,100)).toBe(false);
    });
    it('shrinks and centers an oversized child until every corner fits inside an ellipse',()=>{
        const result=constrainChildToShape({x:-20,y:-20,width:180,height:120},220,180,18,'ELLIPSE');
        expect(result.width).toBeLessThan(180);
        expect(result.height).toBeLessThan(120);
        for(const point of [{x:result.x,y:result.y},{x:result.x+result.width,y:result.y},{x:result.x,y:result.y+result.height},{x:result.x+result.width,y:result.y+result.height}])
            expect(pointInsideShape('ELLIPSE',point,220,180,18)).toBe(true);
    });
});
